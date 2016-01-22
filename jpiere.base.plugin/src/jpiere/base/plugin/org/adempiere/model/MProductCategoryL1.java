package jpiere.base.plugin.org.adempiere.model;

import java.sql.ResultSet;
import java.util.Properties;

/**
 * JPIERE-0148
 *
 * @author Hideaki Hagiwara
 *
 */
public class MProductCategoryL1 extends X_JP_ProductCategoryL1 {

	public MProductCategoryL1(Properties ctx, int JP_ProductCategoryL1_ID, String trxName) {
		super(ctx, JP_ProductCategoryL1_ID, trxName);
	}

	public MProductCategoryL1(Properties ctx, ResultSet rs, String trxName) {
		super(ctx, rs, trxName);
	}

	@Override
	protected boolean beforeSave(boolean newRecord) {
		return super.beforeSave(newRecord);
	}



}