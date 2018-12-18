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
package jpiere.base.plugin.org.adempiere.callout;

import java.math.BigDecimal;
import java.util.Properties;

import org.adempiere.base.IColumnCallout;
import org.compiere.model.GridField;
import org.compiere.model.GridTab;

import jpiere.base.plugin.org.adempiere.model.MContractLine;

/**
 *
 *  JPiere Contract Process Schedule
 *
 *  JPIERE-0431:JPBP
 *
 * @author Hideaki Hagiwara
 *
 */
public class JPiereContractPSInvoiceLineCallout implements IColumnCallout {

	@Override
	public String start(Properties ctx, int WindowNo, GridTab mTab, GridField mField, Object value, Object oldValue)
	{

		if(mField.getColumnName().equals("JP_ContractLine_ID"))
		{

			if( value != null)
			{
				int JP_ContractLine_ID =  Integer.parseInt(value.toString());
				MContractLine contentLine= MContractLine.get(ctx, JP_ContractLine_ID);
				GridField[] fields = mTab.getFields();
				String columnName = null;
				int columnIndex = -1;
				Object objectValue = null;
				for(int i = 0 ; i < fields.length; i++)
				{
					columnName = fields[i].getColumnName();
					columnIndex = -1;
					objectValue = null;
					if(columnName.equals("JP_ContractPSLine_ID")
							|| columnName.equals("JP_ContractPSInvoiceLine_ID")
							|| columnName.equals("JP_ContractPSInvoiceLine_UU")
							|| columnName.equals("AD_Client_ID")
							|| columnName.equals("IsActive")
							|| columnName.equals("Created")
							|| columnName.equals("CreatedBy")
							|| columnName.equals("Updated")
							|| columnName.equals("UpdatedBy")
							|| columnName.equals("Line")
							|| columnName.equals("LineNetAmt")
							|| columnName.equals("Processed")
						)
					{
						continue;
					}

					if(!fields[i].isAllowCopy())
						continue;

					columnIndex = contentLine.get_ColumnIndex(columnName);
					if(columnIndex > -1)
					{
						objectValue = contentLine.get_Value(columnIndex);
						if(columnName.equals("QtyEntered"))
						{
							columnIndex = contentLine.get_ColumnIndex("QtyInvoiced");
							objectValue = contentLine.get_Value(columnIndex);
							mTab.setValue("QtyEntered", objectValue);

						}else if(columnName.equals("C_UOM_ID")){

							int C_UOM_ID = 0;
							if(contentLine.getM_Product_ID() > 0)
								C_UOM_ID = contentLine.getM_Product().getC_UOM_ID();
							else
								C_UOM_ID = contentLine.getC_UOM_ID();

							mTab.setValue("C_UOM_ID", C_UOM_ID);

						}else if(objectValue != null){

							mTab.setValue(columnName, objectValue);
						}
					}

				}//for

				BigDecimal qty = (BigDecimal)mTab.getValue("QtyEntered");
				BigDecimal amt = (BigDecimal)mTab.getValue("PriceEntered");

				mTab.setValue("LineNetAmt", qty.multiply(amt));
			}
		}

		return "";
	}

}
