package org.apache.catalina;

public interface Lifecycle {

    // ----------------------------------------------------- Manifest Constants

    /**
     * 组件启动时触发
     */
    public static final String START_EVENT = "start";

    public static final String BEFORE_START_EVENT = "before_start";

    public static final String AFTER_START_EVENT = "after_start";

    /**
     * 组件关闭时触发
     */
    public static final String STOP_EVENT = "stop";

    public static final String BEFORE_STOP_EVENT = "before_stop";

    public static final String AFTER_STOP_EVENT = "after_stop";

    // --------------------------------------------------------- Public Methods

    /**
     * 生命周期监听器相关
     */
    public void addLifecycleListener(LifecycleListener listener);

    public LifecycleListener[] findLifecycleListeners();

    public void removeLifecycleListener(LifecycleListener listener);

    /**
     * 组件必须提供start()和stop()方法，实现对其启动/关闭操作
     */
    public void start() throws LifecycleException;

    public void stop() throws LifecycleException;

}
