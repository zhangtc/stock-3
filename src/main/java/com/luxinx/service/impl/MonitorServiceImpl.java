package com.luxinx.service.impl;

import com.luxinx.db.IDao;
import com.luxinx.service.MonitorService;
import com.luxinx.task.Stock;
import com.luxinx.util.HttpUtil;
import com.luxinx.util.MailUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.mail.MessagingException;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MonitorServiceImpl implements MonitorService {
    private static final Logger logger = LoggerFactory.getLogger(MonitorServiceImpl.class);
    @Autowired
    public IDao dao;

    @Override
    public void monitorDailyPrice() {
        logger.info("============================start===================================");
        if (Stock.STOCK_CODE_FOCUS.isEmpty()) {
            String sql = "select stockcode,stockname,destprice,updown,issend from tb_stock_focus";
            Stock.STOCK_CODE_FOCUS = dao.executeQuery(sql);
        }
        for (Map<String, Object> focus : Stock.STOCK_CODE_FOCUS) {
            String code = focus.get("stockcode") + "";
            String precode;
            if (code.startsWith("0") || code.startsWith("3")) {
                precode = "sz";
            } else if (code.startsWith("6")) {
                precode = "sh";
            } else {
                precode = "";
            }
            try {
                long st = System.currentTimeMillis();
                String current = HttpUtil.doGet("http://hq.sinajs.cn/list=" + precode + code);
                String[] cuuarry = current.split(",");
				/*Map<String,String> daymp = new HashMap<>();
				daymp.put("id", code);
				daymp.put("o", cuuarry[1]);//今开
				daymp.put("c", cuuarry[2]);//昨收
				daymp.put("dq", cuuarry[3]);//当前
				daymp.put("d", cuuarry[30]+" "+cuuarry[31]);//日期*/
                double destprice = 0.0;
                if (focus.get("destprice") != null && !"".equals(focus.get("destprice"))) {
                    destprice = Double.parseDouble(focus.get("destprice") + "");
                }
                double currprice = 0.0;
                String strcurrprice = "";
                if (cuuarry.length > 3) {
                    strcurrprice = cuuarry[3];
                }
                if (strcurrprice != null && !"".equals(strcurrprice)) {
                    currprice = Double.parseDouble(strcurrprice);
                }
                double detaprice = currprice - destprice;
                String updown = focus.get("updown") + "";
                String issend = focus.get("issend") + "";
                if ("0".equals(issend)) {
                    if ("-1".equals(updown)) {
                        if (detaprice < 0) {
                            String message = getPrecentStr(focus, destprice, currprice, detaprice, " 已经跌破");
                            EmailNotice(focus, message);
                        }
                    }
                    if ("1".equals(updown)) {
                        if (detaprice > 0) {
                            String message = getPrecentStr(focus, destprice, currprice, detaprice, " 已经涨过");
                            EmailNotice(focus, message);
                        }
                    }
                }
                long ed = System.currentTimeMillis();
                logger.info((ed - st) + "ms");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        logger.info("============================end===================================");

    }

    @Override
    public Map<String, String> choiceGoodStock(String code) {
        String sql25avg = "select closeprice,vol,tn.stockname from tb_stock_history th,tb_stock_name tn  where th.stockcode=tn.stockid and stockcode='" + code + "' order by datestr DESC limit 25";
        List<Map<String, Object>> listprice = dao.executeQuery(sql25avg);
        if(listprice==null||listprice.isEmpty()){
            return new HashMap<>();
        }
        double vollast=0;
        double volbeforelast=0;
        double trend = 0;
        String stockname="";
        if (listprice != null && listprice.size() > 2) {
            vollast=Double.parseDouble(listprice.get(0).get("vol") + "");
            volbeforelast = Double.parseDouble(listprice.get(1).get("vol") + "");
            trend = (vollast / volbeforelast);
            stockname=listprice.get(0).get("stockname")+"";
        }

        final BigDecimal[] total = {new BigDecimal(0)};
        total[0].setScale(2);
        listprice.forEach((Map<String, Object> e) -> {
            total[0] = total[0].add(new BigDecimal(e.get("closeprice") + ""));
        });
        logger.info(stockname+":25avgPrice: " + total[0].divide(new BigDecimal(listprice.size())));

        Map<String, String> result = new HashMap<>();
        //判断成交量比前一日放量才获取25日均线
        if (trend > 0) {
            //获取一只股票25日平均值
            String price25avg = total[0].divide(new BigDecimal(listprice.size())).doubleValue() + "";
            result.put(code, price25avg);
            String deletesql = "delete from tb_stock_focus where stockcode=" + code;
            dao.execute(deletesql);
            String insertfocus = "insert into tb_stock_focus (stockcode,stockname,destprice,updown,issend,datecreated)values('" + code + "','"+stockname+"','" + price25avg + "',1,0,NOW())";
            dao.execute(insertfocus);
            logger.info(insertfocus);
        }
        return result;
    }

    /**
     * Send Email
     *
     * @param focus   the stock of focus
     * @param message email notice message
     */
    private void EmailNotice(Map<String, Object> focus, String message) {
        try {
            MailUtil.sendMessage("javalusir@163.com", message);
        } catch (MessagingException e) {
            e.printStackTrace();
        }
        focus.put("issend", "1");
        String updatesql = "update tb_stock_focus set issend='1' where stockcode=" + focus.get("stockcode");
        dao.execute(updatesql);
        logger.info("Email send...");
    }


    /**
     * get stock Percent
     *
     * @param focus
     * @param destprice
     * @param currprice
     * @param detaprice
     * @param s
     * @return
     */
    private String getPrecentStr(Map<String, Object> focus, double destprice, double currprice, double detaprice, String s) {
        String stockcode = focus.get("stockcode") + "";
        String stockname = focus.get("stockname") + "";
        String strprecent = ((detaprice / destprice) * 100) + "";
        if (strprecent.length() > 4) {
            strprecent = strprecent.substring(0, 4);
        }
        return "股票(" + stockcode + ") " + stockname + s + destprice + "元，当前价格为" + currprice + "元。距目标价振幅" + strprecent + "%";
    }
}
