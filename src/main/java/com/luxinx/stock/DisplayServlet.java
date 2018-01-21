package com.luxinx.stock;

import com.alibaba.fastjson.JSONObject;
import com.luxinx.db.IDao;
import org.springframework.beans.factory.annotation.Autowired;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Display web price
 */
@WebServlet("/displays")
public class DisplayServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    @Autowired
    public IDao dao;

    /**
     * @see HttpServlet#HttpServlet()
     */
    public DisplayServlet() {
    }

    /**
     * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
     */
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        doPost(request, response);
    }

    /**
     * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
     */
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setCharacterEncoding("utf-8");
        String param = request.getParameter("param");
        String query = "";
        if (param != null && !"".equals(param)) {
            param = param.toLowerCase();
            if (param.contains("<") || param.contains(">") || param.contains(",") || param.contains("select") || param.contains("and") || param.contains("or") || param.contains("where")) {
                return;
            }

            if (param.startsWith("0") || param.startsWith("6")) {
                query = "and stockcode like '" + param + "%'";
            } else {
                query = "and stockname like '" + param + "%'";
            }
        }

        String sql = "select t.stockcode ,t.currprice/t.low cl,t.currprice ,t.avgprice ,n.stockname stockname FROM tb_stock_lowest t,tb_stock_name n where t.stockcode=n.stockid and t.currprice/t.low > 0 " + query + " ORDER BY t.currprice/t.low ASC  limit 0,200";
        List<Map<String, Object>> result = dao.executeQuery(sql);//DBConnection.executeQuery(sql);
        String strresult = JSONObject.toJSONString(result);
        response.getWriter().println(strresult);

    }

}
