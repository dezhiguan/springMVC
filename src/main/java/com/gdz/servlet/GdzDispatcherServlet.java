package com.gdz.servlet;
import com.gdz.anntation.GdzAutowired;
import com.gdz.anntation.GdzController;
import com.gdz.anntation.GdzReqeustMapping;
import com.gdz.anntation.GdzService;
import com.gdz.utils.StringUtils;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


/**
 * @Author: guandezhi
 * @Date: 2019/1/20 20:33
 */
public class GdzDispatcherServlet extends HttpServlet {

    private Properties property = new Properties();

    private static final String LOCATION = "contextConfigLocation";

    //保存所有被扫描到的类名
    private Vector<String> classNames = new Vector<>();

    //核心ioc容器，保存所有初始化的bean
    private ConcurrentHashMap<String, Object> ioc = new ConcurrentHashMap<>();

    //保存所有的url和方法的映射关系
    private ConcurrentHashMap<String, Method> handlerMapping = new ConcurrentHashMap();


    @Override
    public void init(ServletConfig config) throws ServletException {

        //1.加载配置文件
        doLoadConfig(config.getInitParameter(LOCATION));

        //2.扫描所有相关的类
        doScanner(property.getProperty("scanPackege"));

        //3.初始化相关类的实例，并保存到ioc容器
        doInstance();

        //4.依赖注入
        doAutowired();

        //5.构造HandleMapping
        initHandlerMapping();


        //6.等到请求，匹配url
        System.out.println(("init is finished 扫描的包路径" + property.getProperty("scanPackege")));
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            dispatch(req, resp);
        } catch (Exception e) {
            resp.getWriter().write("500 Exception Detail " + Arrays.toString(e.getStackTrace()).replaceAll("\\[|\\]", "").
                    replaceAll("\\s", "\\r\n"));
        }
    }

    private void dispatch(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (this.handlerMapping.isEmpty()) {return;}

        String url = request.getRequestURI();
        url = url.replaceAll(request.getContextPath(), "").replaceAll("/+", "/");

        if (!this.handlerMapping.containsKey(url)) {
            response.getWriter().write("404 Not Found !");
            return;
        }

        Method method = this.handlerMapping.get(url);
        //获取方法参数列表
        Class<?>[] paramterTypes = method.getParameterTypes();
        //获取请求参数列表
        Map<String, String[]> paramMap = request.getParameterMap();
        //保存参数值
        Object[] paramterValues = new Object[paramterTypes.length];

        for (int i = 0; i < paramterTypes.length; i++) {
            Class<?> paramType = paramterTypes[i];
            if (paramType == HttpServletRequest.class) {
                paramterValues[i] = request;
                continue;
            } else if (paramType == HttpServletResponse.class) {
                paramterValues[i] = response;
                continue;
            } else if(paramType == String.class) {
                for (Map.Entry<String, String[]> entry : paramMap.entrySet()) {
                    String value = Arrays.toString(entry.getValue()).replaceAll("\\[|\\]", "").replaceAll("\\s", "");
                    paramterValues[i] = value;
                }
            }
        }

        try {
            String beanName = StringUtils.toLowerCase(method.getDeclaringClass().getSimpleName());
            method.invoke(this.ioc.get(beanName), paramterValues);
        } catch (Exception e) {
            e.getMessage();
        }
    }

    private void doLoadConfig(String location) {
        InputStream is = null;

        try {
            is = this.getClass().getClassLoader().getResourceAsStream(location);
            property.load(is);
        } catch (Exception e) {
            System.out.println(("doLoadConfig error"));
        } finally {
            try {
                if (is != null) {is.close();}
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void doScanner(String packageName) {
        //将包的路径转化为文件路径
        URL url = this.getClass().getClassLoader().getResource("/" + packageName.replaceAll("\\.", "/"));
        File dir = new File(url.getFile());
        //如果是文件夹，继续递归
        for (File file : dir.listFiles()) {
            if (file.isDirectory()) {
                doScanner(packageName + "." + file.getName());
            } else {
                classNames.add(packageName + "." + file.getName().replaceAll(".class", "").trim());
            }
        }
    }

    private void doInstance() {
        if (classNames.size() == 0) {return;}

        try {
            for (String className : classNames) {
                Class<?> clazz = Class.forName(className);
                if (clazz.isAnnotationPresent(GdzController.class)) {
                    //默认将首字母小写作为beanName
                    String beanName = StringUtils.toLowerCase(clazz.getSimpleName());
                    ioc.put(beanName, clazz.newInstance());
                } else if (clazz.isAnnotationPresent(GdzService.class)) {
                    GdzService service = clazz.getAnnotation(GdzService.class);
                    String beanName = service.value();
                    if (!"".equals(beanName)) {
                        ioc.put(beanName, clazz.newInstance());
                        System.out.println("obj" + clazz.newInstance());
                        continue;
                    }
                    //如果自己没设置，就按接口类型创建一个实例
                    Class<?>[] interfaces = clazz.getInterfaces();
                    for (Class i : interfaces) {
                        ioc.put(StringUtils.toLowerCase(i.getSimpleName()), clazz.newInstance());
                    }
                } else {
                    continue;
                }
            }
        } catch (Exception e) {
            e.getMessage();
        }

    }


    private void doAutowired() {
        if (ioc.isEmpty()) {return;}

        for (Map.Entry entry : ioc.entrySet()) {
            //拿到实例对象中的所有属性
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : fields) {
                GdzAutowired autowired = field.getAnnotation(GdzAutowired.class);
                String beanName = autowired.value();
                if ("".equals(beanName)) {
                    beanName = field.getType().getSimpleName();
                }

                field.setAccessible(true);

                try {
                    field.set(entry.getValue(), ioc.get(StringUtils.toLowerCase(beanName)));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                    continue;
                }
            }
        }
    }


    private void initHandlerMapping() {
        if (ioc.isEmpty()) {return;}

        for (Map.Entry entry : ioc.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();
            if (!clazz.isAnnotationPresent(GdzController.class)) {continue;}

            String baseUrl = "";
            if (clazz.isAnnotationPresent(GdzController.class)) {
                GdzReqeustMapping requestMapping = clazz.getAnnotation(GdzReqeustMapping.class);
                baseUrl = requestMapping.value();
            }

            //获取method的url
            Method[] methods = clazz.getMethods();
            for (Method method : methods) {
                if (!method.isAnnotationPresent(GdzReqeustMapping.class)) {continue;};
                GdzReqeustMapping requestMapping = method.getAnnotation(GdzReqeustMapping.class);
                String url = ("/" + baseUrl + "/" + requestMapping.value()).replaceAll("/+", "/");
                handlerMapping.put(url, method);
                System.out.println("mapped: " + url + "; method " + method);;
            }
        }
    }

}
