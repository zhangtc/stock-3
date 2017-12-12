package com.luxinx.util;

import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class HttpUtil {

	private static Logger log = Logger.getLogger(HttpUtil.class);
	public static String doGet(String url) throws ClientProtocolException, IOException{

		long start = System.currentTimeMillis();
		HttpEntity entity1 = new StringEntity("");
		String returnstr="";
        CloseableHttpClient httpclient = HttpClients.createDefault();
        try {
        	
            HttpGet httpGet = new HttpGet(url);
            CloseableHttpResponse response1 = httpclient.execute(httpGet);
            
            long end = System.currentTimeMillis();
            log.info("request:"+url+" "+(end-start)+"ms");
            try {
            	if(response1.getStatusLine().getStatusCode()==404){
                	return "";
                }
                entity1 = response1.getEntity();
                returnstr=EntityUtils.toString(entity1);
            } finally {
                response1.close();
            }
        	
        } finally {
            httpclient.close();
        }
		return returnstr;
    
	}
	/**
	 * 
	 * @param reponse
	 * @param type 1:name;2:name&price
	 * @return
	 */
	public static Map<String,String> dealResponse(String reponse,int type){
		Map<String,String> map = new HashMap<String,String>();
		String[] strarry = reponse.split("=");//解析成两段
		if(strarry[1].length()>10){//获得股票代码
			String str = strarry[0];
			String code = new String();
			if(str.contains("sh")){
				code= str.substring(str.indexOf("sh")+2);
			}else{
				code = str.substring(str.indexOf("sz")+2);
			}
			String[] contarry = strarry[1].split(",");
			map.put(code, contarry[0].replace("\"", ""));
			if(type==2){
				map.put("price", contarry[2]);
			}
		}
		return map;
		
	}
}
