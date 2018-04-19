package com.dtxxsoft.expense.ar.invoice;



import com.dtxxsoft.adf.model.BaseApplicationModule;
import com.dtxxsoft.adf.model.BaseViewObject;
import com.dtxxsoft.adf.model.dao.ProcedureStatementSetter;
import com.dtxxsoft.expense.ar.invoice.common.ExpArInoviceAM;

import com.dtxxsoft.expense.model.BorrowVORowImpl;

import java.sql.SQLException;
import java.sql.Types;

import java.util.List;

import oracle.jbo.Row;
import oracle.jbo.domain.Number;
import oracle.jbo.server.ViewLinkImpl;
import oracle.jbo.server.ViewObjectImpl;

import org.apache.commons.lang.StringUtils;


// ---------------------------------------------------------------------
// ---    File generated by Oracle ADF Business Components Design Time.
// ---    Thu May 18 22:29:55 CST 2017
// ---    Custom code may be added to this class.
// ---    Warning: Do not modify method signatures of generated methods.
// ---------------------------------------------------------------------
public class ExpArInoviceAMImpl extends BaseApplicationModule implements ExpArInoviceAM {
    /**
     * This is the default constructor (do not remove).
     */
    public ExpArInoviceAMImpl() {
    }

    /**
     * Container's getter for ExpArInvoiceVO.
     * @return ExpArInvoiceVO
     */
    public ViewObjectImpl getExpArInvoiceVO() {
        return (ViewObjectImpl)findViewObject("ExpArInvoiceVO");
    }

    /**
     * Container's getter for ExpArInvoiceLineVO1.
     * @return ExpArInvoiceLineVO1
     */
    public ViewObjectImpl getExpArInvoiceLineVO1() {
        return (ViewObjectImpl)findViewObject("ExpArInvoiceLineVO1");
    }

    /**
     * Container's getter for ExpArInvoiceVL1.
     * @return ExpArInvoiceVL1
     */
    public ViewLinkImpl getExpArInvoiceVL1() {
        return (ViewLinkImpl)findViewLink("ExpArInvoiceVL1");
    }
    
    /**
     * TaskFlow路由
     * @param dataId
     * @return
     */
    public String initRouter(String dataId, String linkId) {
      if (StringUtils.isNumeric(dataId)) {
            try {
                this.getExpArInvoiceVO().setApplyViewCriteriaName("FindByDataId");
                this.getExpArInvoiceVO().setNamedWhereClauseParam("BindDataId", new Number(dataId));
                this.getExpArInvoiceVO().executeQuery();
                return "wfcallback";
            } catch (SQLException e) {
                logger.warning("工作流回调出错", e);
            }
        }
        if (StringUtils.isNumeric(linkId)) {
            try {
                this.getExpArInvoiceVO().setApplyViewCriteriaName("FindByDataId");
                this.getExpArInvoiceVO().setNamedWhereClauseParam("BindDataId", new Number(linkId));
                this.getExpArInvoiceVO().executeQuery();
                return "linkdetail";
            } catch (SQLException e) {
                logger.warning("查看详细信息出错", e);
            }
        }
        
//        ViewObjectImpl vo = this.getExpArInvoiceVO();
//        String sql = vo.getWhereClause();
//        if (sql != null && sql.length() > 0) {
//            if (sql.equals("null")) 
//                sql = sql + "  CREATED_BY = fnd_global.USER_ID";
//             else 
//                sql = sql + " and CREATED_BY = fnd_global.USER_ID";
//        } else
//            sql = "  CREATED_BY = fnd_global.USER_ID";
//        vo.setWhereClause(sql);
//        vo.executeQuery();
        return "invoiceList";
    }
    
    private Number getTemplateId(String templateCode) {
        String template = (templateCode == null) ? "ARINVOICE" : templateCode;
        String sql = "select to_char(template_header_id) FROM cux_expense_template_headers where org_id = spm_sso_pkg.getOrgId and block_code = '" + template + "' and rownum < 2";
        String templateId = jdbcTemplate.queryForString(sql, null);
        try {
            return new Number(templateId);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return new Number(0);
    }
    
    public void initCreateRow(String templateCode) {
       Number templateId = this.getTemplateId(templateCode);
        ViewObjectImpl dataVO = this.getExpArInvoiceVO();
        Row targetRow = dataVO.createRow();
        targetRow.setNewRowState(Row.STATUS_INITIALIZED);
        targetRow.setAttribute("TemplateId", templateId);
        dataVO.insertRow(targetRow);
    }
    
    public String getTaxAmountFromDb(Number taxId,Number lineAmount){
        return this.jdbcTemplate.queryForString("select cux_expense_ar_pkg.get_tax_amount(?,?) from dual",new Object[]{taxId,lineAmount});
    }
    
    /**
     * 启动工作流
     */
    public String startWorkflow() {
        this.getDBTransaction().commit();
        ViewObjectImpl dataVO = this.getExpArInvoiceVO();
        ExpArInvoiceVORowImpl dataRow = (ExpArInvoiceVORowImpl)dataVO.getCurrentRow();
        String returnMsg = "Y"; 
        if (dataRow != null && (dataRow.getAuditStatus().equals("A") || dataRow.getAuditStatus().equals("D"))) {
            String strSql = " begin spm_wf_common_pkg.Start_Wf(?,?,?); end;";
            final String headId =  dataRow.getInvoiceId().toString();
            List list = this.jdbcTemplate.executeProcedure(strSql, new ProcedureStatementSetter() {
                    @Override
                    public void registerParameters() throws SQLException {
                        this.registerInParameter(headId);
                        this.registerInParameter("EXPENSE_ARINVOICE" );
                        this.registerOutParameter(Types.VARCHAR, 0, 2000);
                    }
                });
            if (list.get(0) != null)
                returnMsg = list.get(0).toString();
        }
        return returnMsg;
    }
    
    public void creditQueryEvent(){
        this.getExpCreditHeaderVO1().setApplyViewCriteriaName("ExpCreditHeaderById");
        this.getExpCreditHeaderVO1().setNamedWhereClauseParam("BindInvoiceId",this.getExpArInvoiceVO().getCurrentRow().getAttribute("InvoiceId"));
        this.getExpCreditHeaderVO1().executeQuery();
    }
    
    public String createCreditInvoice(){
        String returnMsg = "Y";
        int num = 0;
        for (Row selectRow : this.getExpCreditLineVO1().getAllRowsInRange()) {
            if (selectRow.getAttribute("SelectFlag") != null){
                if ("Y".equals(selectRow.getAttribute("SelectFlag")))
                    num++;
            }
        }
        if (num == 0)
            return returnMsg = "亲,至少要选择一个贷记行！";
        returnMsg = createCreditFromDb();
        return returnMsg;
    }

    private String createCreditFromDb() {
        String returnMsg = "Y";
        Row hrow = getExpCreditHeaderVO1().getCurrentRow();
        final int invoiceId = ((Number)hrow.getAttribute("InvoiceId")).intValue();
        final String invoiceNum = (String)hrow.getAttribute("CreditNum");
        final int typeId = ((Number)hrow.getAttribute("CreditMemoTypeId")).intValue();
        final String gldate = hrow.getAttribute("GlDate").toString();
        final String comments = hrow.getAttribute("Comments").toString();
        Integer creaditId =
            this.jdbcTemplate.executeProcedureForInt("begin cux_expense_ar_pkg.generate_credit_h_event(?,?,?,?,?,?); end;",
                                                     new ProcedureStatementSetter() {
                @Override
                public void registerParameters() throws SQLException {
                    this.registerInParameter(invoiceId);
                    this.registerInParameter(invoiceNum);
                    this.registerInParameter(typeId);
                    this.registerInParameter(gldate);
                    this.registerInParameter(comments);
                    this.registerOutParameter(Types.INTEGER, 0, 20000);
                }
            });
        int creaditIdd = creaditId.intValue();
        if (creaditIdd != -1) {
            String lsql = "begin cux_expense_ar_pkg.generate_credit_l_event(?,?,?,?,?); end;";
            for (Row selectRow : this.getExpCreditLineVO1().getAllRowsInRange()) {
                if (selectRow.getAttribute("SelectFlag") != null) {
                    if ("Y".equals(selectRow.getAttribute("SelectFlag")))
                        this.jdbcTemplate.executeUpdate(lsql,
                                                        new Object[] { creaditIdd, selectRow.getAttribute("LineId"),
                                                                       selectRow.getAttribute("LineAmount"),
                                                                       selectRow.getAttribute("TaxAmount"),
                                                                       selectRow.getAttribute("IncomeAmount") });
                }
            }
            returnMsg = "Y-贷记编号为" + invoiceNum + "成功！";
            this.getExpCreditInvoiceVO1().executeQuery();
        } else
            returnMsg = "贷记编号为" + invoiceNum + "失败！";
        return returnMsg;
    }
    
    public void setWriteOffFilter(String filterType){
        ViewObjectImpl dataVO = null;
        if ("CMWRITEOFF".equals(filterType))
          dataVO = this.getExpArWriteoffVO1();
        if ("INVOICEWRITEOFF".equals(filterType))
          dataVO = this.getExpArWriteoffCashVO1();
        String filterIds = "-1";
        for (Row temp:dataVO.getAllRowsInRange()){
            if (temp.getAttribute("WriteoffInvoiceId") != null){
                if ("-1".equals(filterIds))
                    filterIds = temp.getAttribute("WriteoffInvoiceId").toString(); 
                else
                    filterIds = filterIds +"," +temp.getAttribute("WriteoffInvoiceId").toString();     
            }
        }
        String strSql = " begin cux_expense_pkg.set_expese_filter_event(?,'"+filterType+"'); end;"; 
        this.jdbcTemplate.executeUpdate(strSql,new Object[] { filterIds}); 
    }

    public void writeoffQueryEvent(String writeoffType){
        Number customid = new Number(-1);
        if (this.getExpArInvoiceVO().getCurrentRow().getAttribute("BillToCustomerId") != null)
            customid = (Number)this.getExpArInvoiceVO().getCurrentRow().getAttribute("BillToCustomerId");
        if ("CMWRITEOFF".equals(writeoffType)){
            this.getExpWriteoffInvoiceVO1().setApplyViewCriteriaName("ExpWriteoffById");
            this.getExpWriteoffInvoiceVO1().setNamedWhereClauseParam("BindBillId",customid);
            this.getExpWriteoffInvoiceVO1().executeQuery();
        }
        if ("INVOICEWRITEOFF".equals(writeoffType)){
             this.getExpArCashWriteoffVO1().setApplyViewCriteriaName("ExpArCashWriteoffByCustom");
             this.getExpArCashWriteoffVO1().setNamedWhereClauseParam("BindCustom",customid);
             this.getExpArCashWriteoffVO1().executeQuery();
        }  
    }
        
    public String createWriteoffInvoice(){
        String returnMsg = "Y";
        int num = 0;
        for (Row selectRow : this.getExpWriteoffInvoiceVO1().getAllRowsInRange()) {
            if (selectRow.getAttribute("CheckFlag") != null){
                if ("Y".equals(selectRow.getAttribute("CheckFlag")))
                    num++;
            }
        }
        if (num == 0)
            return returnMsg = "亲,至少要选择一个核销对象！";
        for (Row selectRow : this.getExpWriteoffInvoiceVO1().getAllRowsInRange()) {
            if (selectRow.getAttribute("CheckFlag") != null){
                if ("Y".equals(selectRow.getAttribute("CheckFlag")))
                    createWriteoffBatch(selectRow,"CM");
            }
        }
        return returnMsg;
    }
   
    public String createWriteoffCash(){
        String returnMsg = "Y";
        int num = 0;
        for (Row selectRow : this.getExpArCashWriteoffVO1().getAllRowsInRange()) {
            if (selectRow.getAttribute("ChoiceFlag") != null){
                if ("Y".equals(selectRow.getAttribute("ChoiceFlag")))
                    num++;
            }
        }
        if (num == 0)
            return returnMsg = "亲,至少要选择一个核销对象！";
        for (Row selectRow : this.getExpArCashWriteoffVO1().getAllRowsInRange()) {
            if (selectRow.getAttribute("ChoiceFlag") != null){
                if ("Y".equals(selectRow.getAttribute("ChoiceFlag")))
                    createWriteoffBatch(selectRow,"INVOICE");
            }
        }
        return returnMsg;
    } 
        private void createWriteoffBatch(Row selectRow,String writeoffType) {
            ViewObjectImpl vo = null;
            if("CM".equals(writeoffType))
               vo = this.getExpArWriteoffVO1();
            if("INVOICE".equals(writeoffType))
               vo = this.getExpArWriteoffCashVO1();   
            Row targetRow = vo.createRow();
            targetRow.setNewRowState(Row.STATUS_INITIALIZED);
            if("CM".equals(writeoffType)){
                targetRow.setAttribute("WriteoffInvoiceId", selectRow.getAttribute("InvoiceId"));
                targetRow.setAttribute("WriteoffType",writeoffType);
                targetRow.setAttribute("ClassName", selectRow.getAttribute("ClassName"));
                targetRow.setAttribute("TypeName", selectRow.getAttribute("TypeName"));
                targetRow.setAttribute("WriteoffNum", selectRow.getAttribute("InvoiceNum"));
                targetRow.setAttribute("GlDate", selectRow.getAttribute("ApplyDate"));
                targetRow.setAttribute("ApplyDate", selectRow.getAttribute("ApplyDate"));
                targetRow.setAttribute("WriteoffAmount", selectRow.getAttribute("ThisAmount"));
                targetRow.setAttribute("SourceInvoiceId", this.getExpArInvoiceVO().getCurrentRow().getAttribute("InvoiceId"));
            }
            if("INVOICE".equals(writeoffType)){
                targetRow.setAttribute("WriteoffInvoiceId", selectRow.getAttribute("BillId"));
                targetRow.setAttribute("WriteoffType",writeoffType);
                targetRow.setAttribute("MethodName", selectRow.getAttribute("EbsTypeName"));
                targetRow.setAttribute("WriteoffNum", selectRow.getAttribute("EbsNum"));
                targetRow.setAttribute("GlDate", selectRow.getAttribute("WriteoffDate"));
                targetRow.setAttribute("ApplyDate", selectRow.getAttribute("WriteoffDate"));
                targetRow.setAttribute("WriteoffAmount", selectRow.getAttribute("ThisAmount"));
                targetRow.setAttribute("SourceInvoiceId", this.getExpArInvoiceVO().getCurrentRow().getAttribute("InvoiceId")); 
            }
            vo.insertRow(targetRow);
        }
    /**
     * Container's getter for ExpCreditInvoiceVO1.
     * @return ExpCreditInvoiceVO1
     */
    public ViewObjectImpl getExpCreditInvoiceVO1() {
        return (ViewObjectImpl)findViewObject("ExpCreditInvoiceVO1");
    }

    /**
     * Container's getter for ExpCreditInvoiceVL1.
     * @return ExpCreditInvoiceVL1
     */
    public ViewLinkImpl getExpCreditInvoiceVL1() {
        return (ViewLinkImpl)findViewLink("ExpCreditInvoiceVL1");
    }

    /**
     * Container's getter for ExpCreditHeaderVO1.
     * @return ExpCreditHeaderVO1
     */
    public ViewObjectImpl getExpCreditHeaderVO1() {
        return (ViewObjectImpl)findViewObject("ExpCreditHeaderVO1");
    }

    /**
     * Container's getter for ExpCreditLineVO1.
     * @return ExpCreditLineVO1
     */
    public ViewObjectImpl getExpCreditLineVO1() {
        return (ViewObjectImpl)findViewObject("ExpCreditLineVO1");
    }

    /**
     * Container's getter for ExpCreditVL1.
     * @return ExpCreditVL1
     */
    public ViewLinkImpl getExpCreditVL1() {
        return (ViewLinkImpl)findViewLink("ExpCreditVL1");
    }

    /**
     * Container's getter for ExpArWriteoffVO1.
     * @return ExpArWriteoffVO1
     */
    public ViewObjectImpl getExpArWriteoffVO1() {
        return (ViewObjectImpl)findViewObject("ExpArWriteoffVO1");
    }

    /**
     * Container's getter for ExpArWriteoffVL1.
     * @return ExpArWriteoffVL1
     */
    public ViewLinkImpl getExpArWriteoffVL1() {
        return (ViewLinkImpl)findViewLink("ExpArWriteoffVL1");
    }

    /**
     * Container's getter for ExpWriteoffInvoiceVO1.
     * @return ExpWriteoffInvoiceVO1
     */
    public ViewObjectImpl getExpWriteoffInvoiceVO1() {
        return (ViewObjectImpl)findViewObject("ExpWriteoffInvoiceVO1");
    }

    /**
     * Container's getter for ExpArEbsWriteoffVO1.
     * @return ExpArEbsWriteoffVO1
     */
    public ViewObjectImpl getExpArEbsWriteoffVO1() {
        return (ViewObjectImpl)findViewObject("ExpArEbsWriteoffVO1");
    }

    /**
     * Container's getter for ExpArEbsWriteOffVL1.
     * @return ExpArEbsWriteOffVL1
     */
    public ViewLinkImpl getExpArEbsWriteOffVL1() {
        return (ViewLinkImpl)findViewLink("ExpArEbsWriteOffVL1");
    }

    /**
     * Container's getter for ExpArAdjustComVO1.
     * @return ExpArAdjustComVO1
     */
    public ViewObjectImpl getExpArAdjustComVO1() {
        return (ViewObjectImpl)findViewObject("ExpArAdjustComVO1");
    }

    /**
     * Container's getter for ExpArAdjustInvoiceVL1.
     * @return ExpArAdjustInvoiceVL1
     */
    public ViewLinkImpl getExpArAdjustInvoiceVL1() {
        return (ViewLinkImpl)findViewLink("ExpArAdjustInvoiceVL1");
    }

    /**
     * Container's getter for ExpArWriteoffCashVO1.
     * @return ExpArWriteoffCashVO1
     */
    public ViewObjectImpl getExpArWriteoffCashVO1() {
        return (ViewObjectImpl)findViewObject("ExpArWriteoffCashVO1");
    }

    /**
     * Container's getter for ExpArWriteoffCashVL1.
     * @return ExpArWriteoffCashVL1
     */
    public ViewLinkImpl getExpArWriteoffCashVL1() {
        return (ViewLinkImpl)findViewLink("ExpArWriteoffCashVL1");
    }

    /**
     * Container's getter for ExpArCashWriteoffVO1.
     * @return ExpArCashWriteoffVO1
     */
    public ViewObjectImpl getExpArCashWriteoffVO1() {
        return (ViewObjectImpl)findViewObject("ExpArCashWriteoffVO1");
    }
    
    /**
     * 退回单据
     */
    public String returnDoc() {
        String s = "";
        ViewObjectImpl dataVO = this.getExpArInvoiceVO();
        ExpArInvoiceVORowImpl dataRow = (ExpArInvoiceVORowImpl)dataVO.getCurrentRow();
        if (dataRow != null) {
            String strSql = " begin cux_expense_ar_pkg.cancel_wf_event(?,?,?); end;";
            final String itemKey = dataRow.getItemKey();
            final String tableName = "CUX_EXPENSE_AR_INVOICES";
            List list = this.jdbcTemplate.executeProcedure(strSql, new ProcedureStatementSetter() {
                    @Override
                    public void registerParameters() throws SQLException {
                        this.registerInParameter(tableName);
                        this.registerInParameter(itemKey);
                        this.registerOutParameter(Types.VARCHAR, 0, 2000);
                    }
                });
            s = list.get(0).toString();
            this.saveScrollPosition(dataVO);
            this.getDBTransaction().commit();
            dataVO.clearCache();
            dataVO.executeQuery();
            this.restoreScrollPosition(dataVO);
        }
        return s;
    }
}
