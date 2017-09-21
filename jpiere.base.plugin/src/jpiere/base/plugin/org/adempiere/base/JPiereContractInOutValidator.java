/******************************************************************************
 * Product: JPiere                                                            *
 * Copyright (C) Hideaki Hagiwara (h.hagiwara@oss-erp.co.jp)                  *
 *                                                                            *
 * This program is free software, you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY.                          *
 * See the GNU General Public License for more details.                       *
 *                                                                            *
 * JPiere is maintained by OSS ERP Solutions Co., Ltd.                        *
 * (http://www.oss-erp.co.jp)                                                 *
 *****************************************************************************/
package jpiere.base.plugin.org.adempiere.base;

import java.util.List;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.MClient;
import org.compiere.model.MDocType;
import org.compiere.model.MInOut;
import org.compiere.model.MInOutLine;
import org.compiere.model.MOrder;
import org.compiere.model.MOrderLine;
import org.compiere.model.MRMA;
import org.compiere.model.MRMALine;
import org.compiere.model.ModelValidationEngine;
import org.compiere.model.ModelValidator;
import org.compiere.model.PO;
import org.compiere.model.Query;
import org.compiere.process.DocAction;
import org.compiere.util.CLogger;
import org.compiere.util.Env;
import org.compiere.util.Msg;
import org.compiere.util.Util;

import jpiere.base.plugin.org.adempiere.model.MContract;
import jpiere.base.plugin.org.adempiere.model.MContractAcct;
import jpiere.base.plugin.org.adempiere.model.MContractCalender;
import jpiere.base.plugin.org.adempiere.model.MContractContent;
import jpiere.base.plugin.org.adempiere.model.MContractLine;
import jpiere.base.plugin.org.adempiere.model.MContractProcPeriod;
import jpiere.base.plugin.org.adempiere.model.MRecognition;
import jpiere.base.plugin.org.adempiere.model.MRecognitionLine;



/**
 *  JPIERE-0363: Contract Management
 *  JPiere Contract InOut Validator
 *
 *  @author  Hideaki Hagiwara（h.hagiwara@oss-erp.co.jp）
 *
 */
public class JPiereContractInOutValidator extends AbstractContractValidator  implements ModelValidator {

	private static CLogger log = CLogger.getCLogger(JPiereContractInOutValidator.class);
	private int AD_Client_ID = -1;
	private int AD_Org_ID = -1;
	private int AD_Role_ID = -1;
	private int AD_User_ID = -1;


	@Override
	public void initialize(ModelValidationEngine engine, MClient client) 
	{
		if(client != null)
			this.AD_Client_ID = client.getAD_Client_ID();
		engine.addModelChange(MInOut.Table_Name, this);
		engine.addModelChange(MInOutLine.Table_Name, this);
		engine.addDocValidate(MInOut.Table_Name, this);

	}

	@Override
	public int getAD_Client_ID() {

		return AD_Client_ID;
	}

	@Override
	public String login(int AD_Org_ID, int AD_Role_ID, int AD_User_ID) {
		this.AD_Org_ID = AD_Org_ID;
		this.AD_Role_ID = AD_Role_ID;
		this.AD_User_ID = AD_User_ID;

		return null;
	}

	@Override
	public String modelChange(PO po, int type) throws Exception
	{
		if(po.get_TableName().equals(MInOut.Table_Name))
		{
			return inOutValidate(po, type);
			
		}else if(po.get_TableName().equals(MInOutLine.Table_Name)){
			
			return inOutLineValidate(po, type);
		}
		
		return null;
	}

	@Override
	public String docValidate(PO po, int timing) 
	{
		
		if(timing == ModelValidator.TIMING_BEFORE_PREPARE)
		{
			int JP_Contract_ID = po.get_ValueAsInt("JP_Contract_ID");
			if(JP_Contract_ID <= 0)
				return null;
			
			MContract contract = MContract.get(Env.getCtx(), JP_Contract_ID);
			if(contract.getJP_ContractType().equals(MContract.JP_CONTRACTTYPE_PeriodContract))
			{
				//Check Mandetory - JP_ContractProcPeriod_ID
				MInOut io = (MInOut)po;
				MInOutLine[] lines = io.getLines();
				int JP_ContractLine_ID = 0;
				int JP_ContractProcPeriod_ID = 0;
				for(int i = 0; i < lines.length; i++)
				{
					JP_ContractLine_ID = lines[i].get_ValueAsInt("JP_ContractLine_ID");
					JP_ContractProcPeriod_ID = lines[i].get_ValueAsInt("JP_ContractProcPeriod_ID");
					if(JP_ContractLine_ID > 0 && JP_ContractProcPeriod_ID <= 0)
					{
						return Msg.getMsg(Env.getCtx(), "FillMandatory") + Msg.getElement(Env.getCtx(), MContractProcPeriod.COLUMNNAME_JP_ContractProcPeriod_ID)
													+ " - " + Msg.getElement(Env.getCtx(),  MInOutLine.COLUMNNAME_Line) + " : " + lines[i].getLine();
					}
				}
				
			}
			
		}//TIMING_BEFORE_PREPARE
		
		
		
		//Create Recognition When Ship/Receipt Complete
		if(timing == ModelValidator.TIMING_AFTER_COMPLETE)
		{
			int JP_Contract_ID = po.get_ValueAsInt("JP_Contract_ID");
			if(JP_Contract_ID <= 0)
				return null;			
			
			MContract contract = MContract.get(Env.getCtx(), JP_Contract_ID);
			if(!contract.getJP_ContractType().equals(MContract.JP_CONTRACTTYPE_PeriodContract)
					&& !contract.getJP_ContractType().equals(MContract.JP_CONTRACTTYPE_SpotContract))
				return null;		
			
			int JP_ContractContent_ID = po.get_ValueAsInt("JP_ContractContent_ID");
			if(JP_ContractContent_ID <= 0)
				return null;
			
			MContractContent content = MContractContent.get(Env.getCtx(), JP_ContractContent_ID);
			if(!content.getJP_Contract_Acct().isPostingRecognitionDocJP())
				return null;
			

			/** Create Recognition*/
			MInOut io = (MInOut)po;			
			String trxName = po.get_TrxName();
			boolean isReversal = io.isReversal();//TODO 出荷納品伝票がリバースされる際に、売上計上伝票もリバースする必要がある。
			MDocType ioDocType = MDocType.get(po.getCtx(), io.getC_DocType_ID());
			
			MOrder order = null;
			MRecognition recognition = null;
			boolean isRMA = false;
			if(io.getC_Order_ID() > 0)
			{
				order = new MOrder(po.getCtx(), io.getC_Order_ID(), trxName);
				MDocType orderDocType = MDocType.get(po.getCtx(), order.getC_DocTypeTarget_ID());
				if(orderDocType.get_ValueAsInt("JP_DocTypeRecognition_ID") == 0)
					return null;
				
				recognition = new MRecognition (order, orderDocType.get_ValueAsInt("JP_DocTypeRecognition_ID") , io.getDateAcct());//JPIERE-0295
				
			}else if(io.getM_RMA_ID() > 0){
			
				isRMA = true;
				MRMA rma = new MRMA(Env.getCtx(),io.getM_RMA_ID(),io.get_TrxName());
				int JP_Order_ID = rma.get_ValueAsInt("JP_Order_ID");
				if(JP_Order_ID == 0)
					return null;
				
				order = new MOrder(po.getCtx(), JP_Order_ID, trxName);
				MDocType orderDocType = MDocType.get(po.getCtx(), order.getC_DocTypeTarget_ID());
				if(orderDocType.get_ValueAsInt("JP_DocTypeRecognition_ID") == 0)
					return null;
				
				recognition = new MRecognition (order, orderDocType.get_ValueAsInt("JP_DocTypeRecognition_ID") , io.getDateAcct());//JPIERE-0295
				recognition.setM_RMA_ID(io.getM_RMA_ID());
			}
			
			
			recognition.setJP_DateRecognized(io.getDateAcct());
			recognition.setM_InOut_ID(io.getM_InOut_ID());
			if (!recognition.save(trxName))
			{
				return "Could not create Invoice: "+ io.getDocumentInfo();//TODO
			}

			MInOutLine[] sLines = io.getLines(false);
			for (int i = 0; i < sLines.length; i++)
			{
				MInOutLine sLine = sLines[i];
				//
				MRecognitionLine rcogLine = new MRecognitionLine(recognition);
				rcogLine.setShipLine(sLine);
				if(isRMA)
				{
					int M_RMALine_ID = rcogLine.getM_RMALine_ID();
					MRMALine rmaLine = new MRMALine(Env.getCtx(),M_RMALine_ID, io.get_TrxName());
					int JP_OrderLine_ID = rmaLine.get_ValueAsInt("JP_OrderLine_ID");
					rcogLine.setC_OrderLine_ID(JP_OrderLine_ID);
				}
				rcogLine.set_ValueNoCheck("JP_ProductExplodeBOM_ID", sLine.get_Value("JP_ProductExplodeBOM_ID"));//JPIERE-0295
				//	Qty = Delivered
				if (sLine.sameOrderLineUOM())
					rcogLine.setQtyEntered(sLine.getQtyEntered());
				else
					rcogLine.setQtyEntered(sLine.getMovementQty());
				rcogLine.setQtyInvoiced(sLine.getMovementQty());
				rcogLine.setJP_QtyRecognized(sLine.getMovementQty());
				rcogLine.setJP_ContractLine_ID(sLine.get_ValueAsInt("JP_ContractLine_ID"));
				rcogLine.setJP_ContractProcPeriod_ID(sLine.get_ValueAsInt("JP_ContractProcPeriod_ID"));
				if (!rcogLine.save(io.get_TrxName()))
				{
					log.warning("Could not create Invoice Line from Shipment Line: "+ recognition.getDocumentInfo());
					return null;
				}

				if (!sLine.save(trxName))
				{
					log.warning("Could not update Shipment line: " + sLine);
				}
			}//for

			if (!recognition.processIt(DocAction.ACTION_Complete))
				throw new AdempiereException("Failed when processing document - " + recognition.getProcessMsg());

			recognition.saveEx(trxName);
			if (!recognition.getDocStatus().equals(DocAction.STATUS_Completed))
			{
				log.warning("Could not Completed Recognition: "+ recognition.getDocumentInfo());
				return null;
			}

		}

		
		return null;
	}
	
	
	/**
	 * Order Validate
	 * 
	 * @param po
	 * @param type
	 * @return
	 */
	private String inOutValidate(PO po, int type)
	{
		
		String msg = derivativeDocHeaderCommonCheck(po, type);	
		if(!Util.isEmpty(msg))
			return msg;
		
		if( type == ModelValidator.TYPE_BEFORE_NEW
				||( type == ModelValidator.TYPE_BEFORE_CHANGE && ( po.is_ValueChanged(MContract.COLUMNNAME_JP_Contract_ID)
						||   po.is_ValueChanged(MContractContent.COLUMNNAME_JP_ContractContent_ID)
						||   po.is_ValueChanged("C_Order_ID") 
						||   po.is_ValueChanged("M_RMA_ID") ) ) )
		{
			
			MInOut io = (MInOut)po;
			int JP_Contract_ID = io.get_ValueAsInt(MContract.COLUMNNAME_JP_Contract_ID);	
			MContract contract = MContract.get(Env.getCtx(), JP_Contract_ID);
			//Check to Change Contract Info		
			if(type == ModelValidator.TYPE_BEFORE_CHANGE && contract.getJP_ContractType().equals(MContract.JP_CONTRACTTYPE_PeriodContract))
			{	
				
				MInOutLine[] contractInvoiceLines = getInOutLinesWithContractLine(io);
				if(contractInvoiceLines.length > 0)
				{
					//Contract Info can not be changed because the document contains contract Info lines.
					return Msg.getMsg(Env.getCtx(), "JP_CannotChangeContractInfoForLines");
				}
			}
			
		}//Type
		
		return null;
	}

	

	/**
	 * Order Line Validate
	 * 
	 * @param po
	 * @param type
	 * @return
	 */
	private String inOutLineValidate(PO po, int type)
	{		
		
		String msg = derivativeDocLineCommonCheck(po, type);	
		if(!Util.isEmpty(msg))
			return msg;
		
		/** Ref:JPiereContractInvoiceValidator  AND JPiereContractRecognitionValidator */
		if(type == ModelValidator.TYPE_BEFORE_NEW
				||( type == ModelValidator.TYPE_BEFORE_CHANGE && ( po.is_ValueChanged(MContractLine.COLUMNNAME_JP_ContractLine_ID)
						||   po.is_ValueChanged("C_OrderLine_ID") ||   po.is_ValueChanged("M_RMALine_ID") ) ))
		{
			MInOutLine ioLine = (MInOutLine)po;							
			int JP_Contract_ID = ioLine.getParent().get_ValueAsInt("JP_Contract_ID");
			int JP_ContractContent_ID = ioLine.getParent().get_ValueAsInt("JP_ContractContent_ID");
			int JP_ContractLine_ID = ioLine.get_ValueAsInt("JP_ContractLine_ID");
			
			if(JP_Contract_ID <= 0)
				return null;
			
			MContract contract = MContract.get(Env.getCtx(), JP_Contract_ID);
			if(!contract.getJP_ContractType().equals(MContract.JP_CONTRACTTYPE_PeriodContract)
					&& !contract.getJP_ContractType().equals(MContract.JP_CONTRACTTYPE_SpotContract))
				return null;
			
			//Check Period Contract & Spot Contract fron now on
			int C_OrderLine_ID = ioLine.getC_OrderLine_ID();
			int M_RMALine_ID = ioLine.getM_RMALine_ID();
			if(C_OrderLine_ID <= 0 && M_RMALine_ID <= 0)
			{
				return "期間契約とスポット契約の場合、受発注伝票明細か返品受付依頼伝票明細の入力は必須です。";//TODOメッセージ化
			}
			
			if(ioLine.getC_OrderLine_ID() > 0)
			{
				if(ioLine.getC_OrderLine().getC_Order_ID() != ioLine.getParent().getC_Order_ID())
					return "期間契約とスポット契約の場合、異なる受発注伝票の明細を含める事はできません。";//TODO メッセージ化
				
			}else if(ioLine.getM_RMALine_ID() > 0){
				
				if(ioLine.getM_RMALine().getM_RMA_ID() != ioLine.getParent().getM_RMA_ID())
					return "期間契約とスポット契約の場合、異なる返品受付依頼伝票の明細を含める事はできません。";//TODO メッセージ化
			}
			
			if(JP_ContractLine_ID <= 0)
				return null;
			
			MContractLine contractLine = MContractLine.get(Env.getCtx(), JP_ContractLine_ID);
			
			//Check Relation of Contract Cotent
			if(contractLine.getJP_ContractContent_ID() != JP_ContractContent_ID)
			{
				//You can select Contract Content Line that is belong to Contract content
				return Msg.getMsg(Env.getCtx(), "Invalid") +" - " +Msg.getElement(Env.getCtx(), "JP_ContractLine_ID") + Msg.getMsg(Env.getCtx(), "JP_Diff_ContractContentLine");
			}
			
			//Check Contract Process Period - Mandetory
			int ioLine_ContractProcPeriod_ID = ioLine.get_ValueAsInt("JP_ContractProcPeriod_ID");
			if(contract.getJP_ContractType().equals(MContract.JP_CONTRACTTYPE_PeriodContract)) 
			{ 
				if(ioLine_ContractProcPeriod_ID <= 0)
				{
					Object[] objs = new Object[]{Msg.getElement(Env.getCtx(), "JP_ContractProcPeriod_ID")};
					return Msg.getMsg(Env.getCtx(), "JP_InCaseOfPeriodContract") + Msg.getMsg(Env.getCtx(),"JP_Mandatory",objs);
					
				}else{
			
					//Check Contract Process Period - Calender
					MContractProcPeriod ioLine_ContractProcPeriod = MContractProcPeriod.get(Env.getCtx(), ioLine_ContractProcPeriod_ID);				
					if(ioLine_ContractProcPeriod.getJP_ContractCalender_ID() != contractLine.getJP_ContractCalender_Inv_ID())
					{
						return "契約書の契約カレンダーの契約処理期間を選択して下さい。";//TODO メッセージ化
					}
				}
			}
			
		}//if(type == ModelValidator.TYPE_BEFORE_NEW)
		
		return null;
	}

	private MInOutLine[] getInOutLinesWithContractLine(MInOut io)
	{
		String whereClauseFinal = "M_InOut_ID=? AND JP_ContractLine_ID IS NOT NULL ";
		List<MInOutLine> list = new Query(Env.getCtx(), MInOutLine.Table_Name, whereClauseFinal, io.get_TrxName())
										.setParameters(io.getM_InOut_ID())
										.setOrderBy(MInOutLine.COLUMNNAME_Line)
										.list();
		return list.toArray(new MInOutLine[list.size()]);
	}
}
