/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.ogris.guaca;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileLock;
import java.util.Base64;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 *
 * @author fjo
 */
public class dip extends HttpServlet {

    /**
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code>
     * methods.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            String redirection = this.getInitParameter("redirection");
            if (redirection == null) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "no redirection");
                return;
            }

            String configfile = this.getInitParameter("configfile");
            if (configfile == null) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "no configfile");
                return;
            }

            HashMap<String, String> reqParams = new HashMap<String, String>();
            Enumeration<String> paramNames = request.getParameterNames();
            while (paramNames.hasMoreElements()) {
                String paramName = paramNames.nextElement();
                reqParams.put(paramName, request.getParameter(paramName));
            }

            if (!reqParams.containsKey("hostname")) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                        "need hostname");
                return;
            }

            if (!reqParams.containsKey("protocol")) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                        "need protocol");
                return;
            }

            String protocol = reqParams.get("protocol");
            if (!reqParams.containsKey("port")) {
                String port;
                if ("vnc".equals(protocol)) {
                    port = "5900";
                } else if ("rdp".equals(protocol)) {
                    port = "3389";
                } else if ("ssh".equals(protocol)) {
                    port = "22";
                } else {
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                            "unknown protocol and no port");
                    return;
                }
                reqParams.put("port", port);
            }

            if ("vnc".equals(protocol) && !reqParams.containsKey("color-depth")) {
                reqParams.put("color-depth", "24");
            }

            String connid = null;

            Document xml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
                    new File(configfile));
            Element docElem = xml.getDocumentElement();

            NodeList configs = docElem.getChildNodes();
            int numConfigs = configs.getLength();

            for (int i = 0; (i < numConfigs) && (connid == null); i++) {
                if ((configs.item(i) instanceof Element)
                        && "config".equals(configs.item(i).getNodeName())) {

                    Element config = (Element) configs.item(i);
                    connid = config.getAttribute("name");

                    HashMap<String, String> cfg = new HashMap<String, String>();
                    cfg.put("protocol", config.getAttribute("protocol"));

                    NodeList params = config.getChildNodes();
                    int numParams = params.getLength();

                    for (int j = 0; j < numParams; j++) {
                        if ((params.item(j) instanceof Element)
                                && "param".equals(params.item(j).getNodeName())) {
                            Element param = (Element) params.item(j);
                            cfg.put(param.getAttribute("name"), param.getAttribute("value"));
                        }
                    }

                    /*
                     * compare query string to xml
                     */
                    for (Map.Entry<String, String> entry : reqParams.entrySet()) {
                        if (!cfg.containsKey(entry.getKey())
                                || !cfg.get(entry.getKey()).equals(entry.getValue())) {
                            connid = null;
                        }
                    }

                    /*
                     * compare xml to query string
                     */
                    for (Map.Entry<String, String> entry : cfg.entrySet()) {
                        if (!reqParams.containsKey(entry.getKey())
                                || !reqParams.get(entry.getKey()).equals(entry.getValue())) {
                            connid = null;
                        }
                    }
                }
            }

            if (connid == null) {
                //connid = "zzz" + UUID.randomUUID().toString();
                Element config = xml.createElement("config");
                docElem.appendChild(config);

                connid = reqParams.get("hostname") + " (" + reqParams.get("protocol");
                config.setAttribute("protocol", reqParams.get("protocol"));
                reqParams.remove("protocol");

                for (Map.Entry<String, String> entry : new TreeMap<String, String>(reqParams).entrySet()) {
                    Element param = xml.createElement("param");
                    config.appendChild(param);
                    param.setAttribute("name", entry.getKey());
                    param.setAttribute("value", entry.getValue());
                    
                    if (!entry.getKey().equals("hostname")) {
                        connid += "," + entry.getKey() + "=" + entry.getValue();
                    }
                }

                connid += ")";
                config.setAttribute("name", connid);

                Transformer trans = TransformerFactory.newInstance().newTransformer();
                trans.setOutputProperty(OutputKeys.INDENT, "yes");
                trans.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
                trans.setOutputProperty(OutputKeys.METHOD, "xml");
                trans.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
                FileOutputStream fos = new FileOutputStream(configfile);
                FileLock lock = fos.getChannel().lock();
                trans.transform(new DOMSource(xml), new StreamResult(fos));
                lock.release();
            }

            connid += '\0' + "c" + '\0' + "noauth";
            connid = Base64.getEncoder().encodeToString(connid.getBytes());

            response.addHeader("Set-Cookie", "JSESSIONID=; path=" + redirection);
            response.addHeader("Set-Cookie", "GUAC_AUTH=; path=" + redirection);
            response.sendRedirect(redirection + "/#/client/" + connid);

        } catch (TransformerConfigurationException e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        } catch (TransformerException e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        } catch (ParserConfigurationException e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        } catch (SAXException e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }

    }

    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
    /**
     * Handles the HTTP <code>GET</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Handles the HTTP <code>POST</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Returns a short description of the servlet.
     *
     * @return a String containing servlet description
     */
    @Override
    public String getServletInfo() {
        return "Short description";
    }// </editor-fold>
}
