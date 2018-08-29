package net.pixelburn.testing;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.*;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class RenderReact extends HttpServlet {

    private static final Logger LOGGER = LoggerFactory.getLogger(RenderReact.class);

    private static final String NASHORON_ENGINE_NAME = "nashorn";

    private static ScriptEngine nashorn;

    private static String componentJSON = null;

    private static Boolean fileLoaded = false;

    private static ScheduledExecutorService globalScheduledThreadPool = Executors.newScheduledThreadPool(20);

    private static void initEngine(HttpServlet servlet) {

        if (nashorn == null) {
            final ScriptEngineManager scriptEngineManager = new ScriptEngineManager(null);
            nashorn = scriptEngineManager.getEngineByName(NASHORON_ENGINE_NAME);

            ScriptContext sc = new SimpleScriptContext();

            sc.setBindings(nashorn.createBindings(), ScriptContext.ENGINE_SCOPE);
            sc.setAttribute("__NASHORN_POLYFILL_TIMER__", globalScheduledThreadPool, ScriptContext.ENGINE_SCOPE);

            javascriptLoader(servlet, "WEB-INF/polyfill.js");
            javascriptLoader(servlet, "WEB-INF/halPlatform.js");

        }
    }

    private static void javascriptLoader(HttpServlet servlet, String jsFilePath) {
        InputStream file;

        try {
            file = servlet.getServletContext().getResourceAsStream(jsFilePath);
            loadFileIntoNashorn(file);

        } catch (ScriptException e) {
            LOGGER.error("Script Exception during loading js file: " + jsFilePath, e);
        }
        LOGGER.info("Java script loader - END");
    }

    private static void loadFileIntoNashorn(InputStream file) throws ScriptException {
        final long currentTimeMillis = System.currentTimeMillis();
        final Reader reader = new InputStreamReader(file, StandardCharsets.UTF_8);

        nashorn.eval(reader);
        LOGGER.info("File is loaded into nashorn in {} ms", System.currentTimeMillis() - currentTimeMillis);
    }

    private static void loadJSON(HttpServlet servlet) {

        if (componentJSON == null) {

            InputStream file = servlet.getServletContext().getResourceAsStream("WEB-INF/titleH1.json");
            try {
                componentJSON = IOUtils.toString(new InputStreamReader(file, StandardCharsets.UTF_8));
            } catch (IOException e) {
                componentJSON = "{}";
            }
        }
    }

    private static void outputPage(HttpServletResponse response, Object htmlObject) {
        try (PrintWriter writer = response.getWriter()) {
            writer.println("<!DOCTYPE html><html>");
            writer.println("<head>");
            writer.println("<meta charset=\"UTF-8\" />");
            writer.println("<title>ReactServlet.java Render</title>");
            writer.println("</head>");
            writer.println("<body>");

            writer.println("<pre>");
            writer.println(componentJSON);
            writer.println("</pre>");

            writer.println(null == htmlObject ? StringUtils.EMPTY : htmlObject.toString());

            writer.println("</body>");
            writer.println("</html>");
        } catch (IOException e) {
            LOGGER.error(e.getMessage());
        }
    }

    @Override
    public void init() {
        loadJSON(this);
        initEngine(this);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        final Invocable invocable = (Invocable) nashorn;

        response.setContentType("text/html");
        response.setCharacterEncoding("UTF-8");

        Object server;
        Object htmlObject = null;

        try {
            server = nashorn.eval("halPlatform");

            //synchronized (nashorn) {
                htmlObject = invocable.invokeMethod(server, "renderReact", componentJSON);
            //}

        } catch (ScriptException | NoSuchMethodException e) {
            LOGGER.error(e.getMessage());
        }

        RenderReact.outputPage(response, htmlObject);
    }

}
