package org.apache.catalina;

public interface LifecycleListener {

    /**
     * 当事件触发时调用
     */
    public void lifecycleEvent(LifecycleEvent event);

}
