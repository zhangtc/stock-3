package com.luxinx.snapapp;

import com.luxinx.db.DBConnection;
import com.luxinx.strategy.Strategy7DaysAvg;
import com.luxinx.util.DateUtil;
import com.luxinx.util.HttpUtil;
import com.luxinx.util.MailUtil;
import org.apache.http.client.ClientProtocolException;
import org.apache.log4j.Logger;

import javax.mail.MessagingException;
import javax.naming.NamingException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

public class CurrentDailyPrice {
	
	public static List<Map<String,String>> STOCK_CODE_FOCUS = new ArrayList<Map<String,String>>();
	
	private Logger log  = Logger.getLogger(CurrentDailyPrice.class);
	
	private boolean TODAY_EXEC = true;
	private boolean ANALY_EXEC = true;
	public CurrentDailyPrice(){
		// crond =
		Timer t = new Timer();
		TimerTask task = new TimerTask() {
			
			@Override
			public void run() {
				setTodayExecTrue();//00:00:00  -- 00:01:00
				if(betweenExec("00:10:00","00:11:00")){
					new StockCodeName().saveAllStocktoDB();
				}
				
				if((betweenExec("09:30:00","11:30:00")||betweenExec("13:00:00","15:00:00"))&&isWorkDay()){
					long strat = System.currentTimeMillis();
					monitor();
					long end = System.currentTimeMillis();
					log.info("shedule spend:"+(end-strat)+"ms");
				}
				
				
				if(afterExec("20:00:00")&&TODAY_EXEC){
					STOCK_CODE_FOCUS.clear();
					new HistoryPrice().getHistoryDailyPrice();
					TODAY_EXEC=false;
				}
				
				if(afterExec("21:30:00")&&ANALY_EXEC){
					new StockLowestPrice().updateLowestAndAvg();
					ANALY_EXEC=false;
				}
				
				if(betweenExec("23:00:00", "23:01:00")){
					try {
						new Strategy7DaysAvg().setTradePrice();//现在只设置天茂的价格
					} catch (ClassNotFoundException e) {
						e.printStackTrace();
					} catch (SQLException e) {
						e.printStackTrace();
					} catch (NamingException e) {
						e.printStackTrace();
					}
				}

			}
		};
		t.schedule(task, 0,1000*60);
	}

	/*public static void main(String[] args) {
		new CurrentDailyPrice().monitor();
	}*/
//-----------------------------------------------------------------------------------------------	
	public boolean betweenExec(String start,String end){
		Date currentdate = DateUtil.getCurrentDate();
		String today = DateUtil.getCurrentStr("yyyy-MM-dd");
		String mintime=today+" "+start;
		String maxtime=today+" "+end;
		Date datemin = DateUtil.getStrToDate(mintime, "yyyy-MM-dd HH:mm:ss");
		Date datemax = DateUtil.getStrToDate(maxtime, "yyyy-MM-dd HH:mm:ss");
		if(currentdate.after(datemin)&&currentdate.before(datemax)){
			return true;
		}
		return false;
	}
	
	public void setTodayExecTrue(){
		
		if(betweenExec("00:00:00","00:01:00")){
			log.info("set setTodayExecTrue");
			TODAY_EXEC=true;
			ANALY_EXEC=true;
		}
	}
	
	public boolean afterExec(String datestr){
		Date currentdate = DateUtil.getCurrentDate();
		String today = DateUtil.getCurrentStr("yyyy-MM-dd");
		String allDaySync=today+" "+datestr;
		Date hostorydaily = DateUtil.getStrToDate(allDaySync, "yyyy-MM-dd HH:mm:ss");
		if(currentdate.after(hostorydaily)){
			return true;
		}else{
			return false;
		}
	}
	
	public void monitor(){
		log.info("============================start===================================");
		if(STOCK_CODE_FOCUS.isEmpty()){
			String sql = "select stockcode,stockname,destprice,updown,issend from tb_stock_focus";
			STOCK_CODE_FOCUS = DBConnection.executeQuery(sql);
		}
		for(Map<String,String> focus:STOCK_CODE_FOCUS){
			String code=focus.get("stockcode");
			String precode = "";
			if(code.startsWith("0")||code.startsWith("3")){
				precode="sz";
			}else if(code.startsWith("6")){
				precode="sh";
			}else{
				precode="";
			}
			try {
				long st = System.currentTimeMillis();
				String current=HttpUtil.doGet("http://hq.sinajs.cn/list="+precode+code);
				String[] cuuarry = current.split(",");
				/*Map<String,String> daymp = new HashMap<>();
				daymp.put("id", code);
				daymp.put("o", cuuarry[1]);//今开
				daymp.put("c", cuuarry[2]);//昨收
				daymp.put("dq", cuuarry[3]);//当前
				daymp.put("d", cuuarry[30]+" "+cuuarry[31]);//日期*/
				double destprice = 0.0;
				if(focus.get("destprice")!=null&&!"".equals(focus.get("destprice"))){
					destprice=Double.parseDouble(focus.get("destprice"));
				}
				double currprice = 0.0;
				String strcurrprice = "";
				if(cuuarry.length>3){
					strcurrprice=cuuarry[3];
				}
				if(strcurrprice!=null&&!"".equals(strcurrprice)){
					currprice=Double.parseDouble(strcurrprice);
				}
				double detaprice = currprice-destprice;
				String updown=focus.get("updown");
				String issend = focus.get("issend");
				if("0".equals(issend)){
					if("-1".equals(updown)){
						if(detaprice<0){
							String stockcode = focus.get("stockcode");
							String stockname = focus.get("stockname");
							
							String strprecent=((detaprice/destprice)*100)+"";
							if(strprecent.length()>4){
								strprecent=strprecent.substring(0, 4);
							}
							String message = "股票("+stockcode+") "+stockname+" 已经跌破"+destprice+"元，当前价格为"+currprice+"元。距目标价振幅"+strprecent+"%";
							try {
								MailUtil.sendMessage("javalusir@163.com", message);
								focus.put("issend", "1");
								log.info("Email send...");
							} catch (MessagingException e) {
								e.printStackTrace();
							}
						}
					}
					StringBuilder sasd = new StringBuilder();
					sasd.append("",1,2);
					if("1".equals(updown)){
						if(detaprice>0){
							String stockcode = focus.get("stockcode");
							String stockname = focus.get("stockname");
							String strprecent=((detaprice/destprice)*100)+"";
							if(strprecent.length()>4){
								strprecent=strprecent.substring(0, 4);
							}
							String message = "股票("+stockcode+") "+stockname+" 已经涨过"+destprice+"元，当前价格为"+currprice+"元。距目标价振幅"+strprecent+"%";
							try {
								MailUtil.sendMessage("javalusir@163.com", message);
								focus.put("issend", "1");
								log.info("Email send...");
							} catch (MessagingException e) {
								e.printStackTrace();
							}
						}
					}
				}
				long ed = System.currentTimeMillis();
				log.info((ed-st)+"ms");
			} catch (ClientProtocolException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} 
		}
		log.info("============================end===================================");
	
	}
	
	/**
	 * 判断是否是工作日
	 * @return
	 */
	private boolean isWorkDay(){
		Calendar cal = Calendar.getInstance();  
        cal.setTime(new Date());  
        return cal.get(Calendar.DAY_OF_WEEK)!=Calendar.SATURDAY&&cal.get(Calendar.DAY_OF_WEEK)!=Calendar.SUNDAY;
	}
	
	public static void main(String[] args) {
		System.out.println(new CurrentDailyPrice().isWorkDay());
	}
	
}
