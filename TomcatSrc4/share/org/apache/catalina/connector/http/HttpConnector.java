package org.apache.catalina.connector.http;

import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.AccessControlException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Stack;
import java.util.Vector;

import org.apache.catalina.Connector;
import org.apache.catalina.Container;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Logger;
import org.apache.catalina.Request;
import org.apache.catalina.Response;
import org.apache.catalina.Service;
import org.apache.catalina.net.DefaultServerSocketFactory;
import org.apache.catalina.net.ServerSocketFactory;
import org.apache.catalina.util.LifecycleSupport;
import org.apache.catalina.util.StringManager;

public final class HttpConnector implements Connector, Lifecycle, Runnable {

    // ----------------------------------------------------- Instance Variables

    /**
     * The <code>Service</code> we are associated with (if any).
     */
    private Service service = null;

    /**
     * ServerSocket可接收socket队列数量
     */
    private int acceptCount = 10;

    /**
     * IP地址，当为null时为所有IP
     */
    private String address = null;

    /**
     * 端口
     */
    private int port = 8080;

    /**
     * The input buffer size we should create on input streams.
     */
    private int bufferSize = 2048;

    /**
     * Servlet容器
     */
    protected Container container = null;

    /**
     * 存放已经创建的processor，相对静态
     * 添加：new processor
     */
    private Vector<HttpProcessor> created = new Vector<HttpProcessor>();

    /**
     * processor对象池栈，对象数量会动态变化(push/pop)，相对动态
     * 入栈：1、初始化；2、对象使用完成
     * 出栈：1、需要使用对象
     */
    private Stack<HttpProcessor> processors = new Stack<HttpProcessor>();

    /**
     * 当前创建的实例数量，相当于created.size()
     */
    private int curProcessors = 0;

    /**
     * The debugging detail level for this component.
     */
    private int debug = 0;

    /**
     * The "enable DNS lookups" flag for this Connector.
     */
    private boolean enableLookups = false;

    /**
     * ServerSocket工厂
     */
    private ServerSocketFactory factory = null;

    private static final String info = "org.apache.catalina.connector.http.HttpConnector/1.0";

    /**
     * 生命周期组件
     */
    protected LifecycleSupport lifecycle = new LifecycleSupport(this);

    /**
     * 初始化对象池中对象数量
     */
    protected int minProcessors = 5;

    /**
     * 对象池中最多的对象数量
     */
    private int maxProcessors = 20;

    /**
     * Timeout value on the incoming connection.
     * Note : a value of 0 means no timeout.
     */
    private int connectionTimeout = Constants.DEFAULT_CONNECTION_TIMEOUT;

    /**
     * The server name to which we should pretend requests to this Connector
     * were directed.  This is useful when operating Tomcat behind a proxy
     * server, so that redirects get constructed accurately.  If not specified,
     * the server name included in the <code>Host</code> header is used.
     */
    private String proxyName = null;

    /**
     * The server port to which we should pretent requests to this Connector
     * were directed.  This is useful when operating Tomcat behind a proxy
     * server, so that redirects get constructed accurately.  If not specified,
     * the port number specified by the <code>port</code> property is used.
     */
    private int proxyPort = 0;

    /**
     * The redirect port for non-SSL to SSL redirects.
     */
    private int redirectPort = 443;

    /**
     * 请求协议
     */
    private String scheme = "http";

    /**
     * The secure connection flag that will be set on all requests received
     * through this connector.
     */
    private boolean secure = false;

    /**
     * ServerSocket实例，用于监听HTTP请求
     */
    private ServerSocket serverSocket = null;

    /**
     * 当前包的SM
     */
    private StringManager sm = StringManager.getManager(Constants.Package);

    private boolean initialized = false;

    /**
     * 启动和停止标志，用于生命周期
     */
    private boolean started = false;

    private boolean stopped = false;

    /**
     * The background thread.
     */
    private Thread thread = null;

    /**
     * 线程名称
     */
    private String threadName = null;

    /**
     * 全局线程同步对象
     */
    private Object threadSync = new Object();

    /**
     * Is chunking allowed ?
     */
    private boolean allowChunking = true;

    /**
     * Use TCP no delay ?
     */
    private boolean tcpNoDelay = true;

    // --------------------------------------------------------- Public Methods

    /**
     * Create (or allocate) and return a Request object suitable for
     * specifying the contents of a Request to the responsible Container.
     */
    public Request createRequest() {

        HttpRequestImpl request = new HttpRequestImpl();
        request.setConnector(this);
        return (request);

    }

    /**
     * Create (or allocate) and return a Response object suitable for
     * receiving the contents of a Response from the responsible Container.
     */
    public Response createResponse() {

        HttpResponseImpl response = new HttpResponseImpl();
        response.setConnector(this);
        return (response);

    }

    // -------------------------------------------------------- Package Methods

    /**
     * 将processor重新入栈
     */
    void recycle(HttpProcessor processor) {

        processors.push(processor);

    }

    // -------------------------------------------------------- Private Methods

    /**
     * 1、同步方法
     * 2、实现了对象池模型：当栈中还有对象时，直接pop；不存在对象且未到maxProcessors，则创建对象；当maxProcessors为负数时，对象数量无限制
     */
    private HttpProcessor createProcessor() {

        synchronized (processors) {
            if (processors.size() > 0) {
                return ((HttpProcessor) processors.pop());
            }

            if ((maxProcessors > 0) && (curProcessors < maxProcessors)) {
                return (newProcessor());
            } else {
                if (maxProcessors < 0) {
                    return (newProcessor());
                } else {
                    return (null);
                }
            }
        }

    }

    /**
     * 创建processor，并初始化生命周期
     */
    private HttpProcessor newProcessor() {

        HttpProcessor processor = new HttpProcessor(this, curProcessors++);
        if (processor instanceof Lifecycle) {
            try {
                ((Lifecycle) processor).start();
            } catch (LifecycleException e) {
                log("newProcessor", e);
                return (null);
            }
        }
        created.addElement(processor);
        return (processor);

    }

    /**
     * 创建ServerSocket监听HTTP请求
     */
    private ServerSocket open() throws IOException, KeyStoreException,
            NoSuchAlgorithmException, CertificateException,
            UnrecoverableKeyException, KeyManagementException {

        // Acquire the server socket factory for this Connector
        ServerSocketFactory factory = getFactory();

        // If no address is specified, open a connection on all addresses
        if (address == null) {
            log(sm.getString("httpConnector.allAddresses"));
            try {
                return (factory.createSocket(port, acceptCount));
            } catch (BindException be) {
                throw new BindException(be.getMessage() + ":" + port);
            }
        }

        // Open a server socket on the specified address
        try {
            InetAddress is = InetAddress.getByName(address);
            log(sm.getString("httpConnector.anAddress", address));
            try {
                return (factory.createSocket(port, acceptCount, is));
            } catch (BindException be) {
                throw new BindException(
                    be.getMessage() + ":" + address + ":" + port);
            }
        } catch (Exception e) {
            log(sm.getString("httpConnector.noAddress", address));
            try {
                return (factory.createSocket(port, acceptCount));
            } catch (BindException be) {
                throw new BindException(be.getMessage() + ":" + port);
            }
        }

    }

    // ---------------------------------------------- Background Thread Methods

    /**
     * 监听HTTP请求，接收Socket并交由processor处理
     */
    public void run() {

        // Loop until we receive a shutdown command
        while (!stopped) {

            // Accept the next incoming connection from the server socket
            Socket socket = null;
            try {

                /**
                 * 阻塞状态，等待接收到socket
                 */
                socket = serverSocket.accept();

                if (connectionTimeout > 0)
                    socket.setSoTimeout(connectionTimeout);

                socket.setTcpNoDelay(tcpNoDelay);

            } catch (AccessControlException ace) {
                log("socket accept security exception", ace);
                continue;
            } catch (IOException e) {
                try {
                    // If reopening fails, exit
                    synchronized (threadSync) {
                        if (started && !stopped)
                            log("accept error: ", e);
                        if (!stopped) {
                            serverSocket.close();
                            serverSocket = open();
                        }
                    }
                } catch (IOException ioe) {
                    log("socket reopen, io problem: ", ioe);
                    break;
                } catch (KeyStoreException kse) {
                    log("socket reopen, keystore problem: ", kse);
                    break;
                } catch (NoSuchAlgorithmException nsae) {
                    log("socket reopen, keystore algorithm problem: ", nsae);
                    break;
                } catch (CertificateException ce) {
                    log("socket reopen, certificate problem: ", ce);
                    break;
                } catch (UnrecoverableKeyException uke) {
                    log("socket reopen, unrecoverable key: ", uke);
                    break;
                } catch (KeyManagementException kme) {
                    log("socket reopen, key management problem: ", kme);
                    break;
                }

                continue;
            }

            // Hand this socket off to an appropriate processor
            HttpProcessor processor = createProcessor();
            if (processor == null) {
                try {
                    log(sm.getString("httpConnector.noProcessor"));
                    socket.close();
                } catch (IOException e) {
                    ;
                }
                continue;
            }
            processor.assign(socket);

            // The processor will recycle itself when it finishes

        }

        // Notify the threadStop() method that we have shut ourselves down
        synchronized (threadSync) {
            threadSync.notifyAll();
        }

    }

    private void threadStart() {

        log(sm.getString("httpConnector.starting"));

        thread = new Thread(this, threadName);

        /**
         * 设置守护线程
         */
        thread.setDaemon(true);

        thread.start();

    }

    private void threadStop() {

        log(sm.getString("httpConnector.stopping"));

        /**
         * 生命周期结束标志
         */
        stopped = true;
        try {
            threadSync.wait(5000);
        } catch (InterruptedException e) {
            ;
        }
        thread = null;

    }

    // ------------------------------------------------------ Lifecycle Methods

    public void addLifecycleListener(LifecycleListener listener) {

        lifecycle.addLifecycleListener(listener);

    }

    public LifecycleListener[] findLifecycleListeners() {

        return lifecycle.findLifecycleListeners();

    }

    public void removeLifecycleListener(LifecycleListener listener) {

        lifecycle.removeLifecycleListener(listener);

    }

    /**
     * 程序入口：初始化connector，创建serverSocket
     */
    public void initialize() throws LifecycleException {
        if (initialized)
            throw new LifecycleException(
                sm.getString("httpConnector.alreadyInitialized"));

        this.initialized = true;
        Exception eRethrow = null;

        try {
            /**
             * 创建serverSocket
             */
            serverSocket = open();
        } catch (IOException ioe) {
            log("httpConnector, io problem: ", ioe);
            eRethrow = ioe;
        } catch (KeyStoreException kse) {
            log("httpConnector, keystore problem: ", kse);
            eRethrow = kse;
        } catch (NoSuchAlgorithmException nsae) {
            log("httpConnector, keystore algorithm problem: ", nsae);
            eRethrow = nsae;
        } catch (CertificateException ce) {
            log("httpConnector, certificate problem: ", ce);
            eRethrow = ce;
        } catch (UnrecoverableKeyException uke) {
            log("httpConnector, unrecoverable key: ", uke);
            eRethrow = uke;
        } catch (KeyManagementException kme) {
            log("httpConnector, key management problem: ", kme);
            eRethrow = kme;
        }

        if (eRethrow != null)
            throw new LifecycleException(threadName + ".open", eRethrow);

    }

    /**
     * 开始处理请求（生命周期管理）
     */
    public void start() throws LifecycleException {

        // Validate and update our current state
        if (started)
            throw new LifecycleException(
                sm.getString("httpConnector.alreadyStarted"));
        threadName = "HttpConnector[" + port + "]";
        lifecycle.fireLifecycleEvent(START_EVENT, null);
        started = true;

        // 真正的开始线程
        threadStart();

        // 创建min个processor
        while (curProcessors < minProcessors) {
            if ((maxProcessors > 0) && (curProcessors >= maxProcessors))
                break;
            HttpProcessor processor = newProcessor();
            recycle(processor);
        }

    }

    /**
     * 结束处理请求（生命周期管理）
     */
    public void stop() throws LifecycleException {

        // Validate and update our current state
        if (!started)
            throw new LifecycleException(
                sm.getString("httpConnector.notStarted"));
        lifecycle.fireLifecycleEvent(STOP_EVENT, null);
        started = false;

        // Gracefully shut down all processors we have created
        for (int i = created.size() - 1; i >= 0; i--) {
            HttpProcessor processor = (HttpProcessor) created.elementAt(i);
            if (processor instanceof Lifecycle) {
                try {
                    // 由于processor也实现了processor接口，故需要调用stop方法
                    ((Lifecycle) processor).stop();
                } catch (LifecycleException e) {
                    log("HttpConnector.stop", e);
                }
            }
        }

        synchronized (threadSync) {
            // Close the server socket we were using
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    ;
                }
            }
            // Stop our background thread
            threadStop();
        }
        serverSocket = null;

    }

    private void log(String message) {

        Logger logger = container.getLogger();
        String localName = threadName;
        if (localName == null)
            localName = "HttpConnector";
        if (logger != null)
            logger.log(localName + " " + message);
        else
            System.out.println(localName + " " + message);

    }

    private void log(String message, Throwable throwable) {

        Logger logger = container.getLogger();
        String localName = threadName;
        if (localName == null)
            localName = "HttpConnector";
        if (logger != null)
            logger.log(localName + " " + message, throwable);
        else {
            System.out.println(localName + " " + message);
            throwable.printStackTrace(System.out);
        }

    }
    // ------------------------------------------------------------- Properties

    public Service getService() {

        return (this.service);

    }

    public void setService(Service service) {

        this.service = service;

    }

    public int getConnectionTimeout() {

        return (connectionTimeout);

    }

    public void setConnectionTimeout(int connectionTimeout) {

        this.connectionTimeout = connectionTimeout;

    }

    public int getAcceptCount() {

        return (acceptCount);

    }

    public void setAcceptCount(int count) {

        this.acceptCount = count;

    }

    public boolean isChunkingAllowed() {

        return (allowChunking);

    }

    public boolean getAllowChunking() {

        return isChunkingAllowed();

    }

    public void setAllowChunking(boolean allowChunking) {

        this.allowChunking = allowChunking;

    }

    public String getAddress() {

        return (this.address);

    }

    public void setAddress(String address) {

        this.address = address;

    }

    public boolean isAvailable() {

        return (started);

    }

    public int getBufferSize() {

        return (this.bufferSize);

    }

    public void setBufferSize(int bufferSize) {

        this.bufferSize = bufferSize;

    }

    public Container getContainer() {

        return (container);

    }

    public void setContainer(Container container) {

        this.container = container;

    }

    public int getCurProcessors() {

        return (curProcessors);

    }

    public int getDebug() {

        return (debug);

    }

    public void setDebug(int debug) {

        this.debug = debug;

    }

    public boolean getEnableLookups() {

        return (this.enableLookups);

    }

    public void setEnableLookups(boolean enableLookups) {

        this.enableLookups = enableLookups;

    }

    public ServerSocketFactory getFactory() {

        if (this.factory == null) {
            synchronized (this) {
                this.factory = new DefaultServerSocketFactory();
            }
        }
        return (this.factory);

    }

    public void setFactory(ServerSocketFactory factory) {

        this.factory = factory;

    }

    public String getInfo() {

        return (info);

    }

    public int getMinProcessors() {

        return (minProcessors);

    }

    public void setMinProcessors(int minProcessors) {

        this.minProcessors = minProcessors;

    }

    public int getMaxProcessors() {

        return (maxProcessors);

    }

    public void setMaxProcessors(int maxProcessors) {

        this.maxProcessors = maxProcessors;

    }

    public int getPort() {

        return (this.port);

    }

    public void setPort(int port) {

        this.port = port;

    }

    public String getProxyName() {

        return (this.proxyName);

    }

    public void setProxyName(String proxyName) {

        this.proxyName = proxyName;

    }

    public int getProxyPort() {

        return (this.proxyPort);

    }

    public void setProxyPort(int proxyPort) {

        this.proxyPort = proxyPort;

    }

    public int getRedirectPort() {

        return (this.redirectPort);

    }

    public void setRedirectPort(int redirectPort) {

        this.redirectPort = redirectPort;

    }

    public String getScheme() {

        return (this.scheme);

    }

    public void setScheme(String scheme) {

        this.scheme = scheme;

    }

    public boolean getSecure() {

        return (this.secure);

    }

    public void setSecure(boolean secure) {

        this.secure = secure;

    }

    public boolean getTcpNoDelay() {

        return (this.tcpNoDelay);

    }

    public void setTcpNoDelay(boolean tcpNoDelay) {

        this.tcpNoDelay = tcpNoDelay;

    }
}
