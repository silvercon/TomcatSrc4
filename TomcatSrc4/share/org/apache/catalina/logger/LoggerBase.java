/*
 * $Header: /home/cvs/jakarta-tomcat-4.0/catalina/src/share/org/apache/catalina/logger/LoggerBase.java,v 1.5 2002/01/25 20:12:20 amyroh Exp $
 * $Revision: 1.5 $
 * $Date: 2002/01/25 20:12:20 $
 *
 * ====================================================================
 *
 * The Apache Software License, Version 1.1
 *
 * Copyright (c) 1999 The Apache Software Foundation.  All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution, if
 *    any, must include the following acknowlegement:
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowlegement may appear in the software itself,
 *    if and wherever such third-party acknowlegements normally appear.
 *
 * 4. The names "The Jakarta Project", "Tomcat", and "Apache Software
 *    Foundation" must not be used to endorse or promote products derived
 *    from this software without prior written permission. For written
 *    permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache"
 *    nor may "Apache" appear in their names without prior written
 *    permission of the Apache Group.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 * [Additional notices, if required by prior licensing conditions]
 *
 */

package org.apache.catalina.logger;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.CharArrayWriter;
import java.io.PrintWriter;

import javax.servlet.ServletException;

import org.apache.catalina.Container;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Logger;

/**
 * Convenience base class for <b>Logger</b> implementations.  The only
 * method that must be implemented is <code>log(String msg)</code>, plus
 * any property setting and lifecycle methods required for configuration.
 *
 * @author Craig R. McClanahan
 * @version $Revision: 1.5 $ $Date: 2002/01/25 20:12:20 $
 */

public abstract class LoggerBase implements Logger {

    // ----------------------------------------------------- Instance Variables

    protected Container container = null;

    protected int debug = 0;

    protected static final String info = "org.apache.catalina.logger.LoggerBase/1.0";

    protected PropertyChangeSupport support = new PropertyChangeSupport(this);

    /**
     * 日志记录等级
     */
    protected int verbosity = ERROR;

    // ------------------------------------------------------------- Properties

    public Container getContainer() {

        return (container);

    }

    public void setContainer(Container container) {

        Container oldContainer = this.container;
        this.container = container;
        support.firePropertyChange("container", oldContainer, this.container);

    }

    public int getDebug() {

        return (this.debug);

    }

    public void setDebug(int debug) {

        this.debug = debug;

    }

    public String getInfo() {

        return (info);

    }

    public int getVerbosity() {

        return (this.verbosity);

    }

    public void setVerbosity(int verbosity) {

        this.verbosity = verbosity;

    }

    /**
     * 设置日志记录等级
     */
    public void setVerbosityLevel(String verbosity) {

        if ("FATAL".equalsIgnoreCase(verbosity))
            this.verbosity = FATAL;
        else if ("ERROR".equalsIgnoreCase(verbosity))
            this.verbosity = ERROR;
        else if ("WARNING".equalsIgnoreCase(verbosity))
            this.verbosity = WARNING;
        else if ("INFORMATION".equalsIgnoreCase(verbosity))
            this.verbosity = INFORMATION;
        else if ("DEBUG".equalsIgnoreCase(verbosity))
            this.verbosity = DEBUG;

    }

    // --------------------------------------------------------- Public Methods

    public void addPropertyChangeListener(PropertyChangeListener listener) {

        support.addPropertyChangeListener(listener);

    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {

        support.removePropertyChangeListener(listener);

    }

    /**
     * 日志记录方法
     */
    public abstract void log(String msg);

    public void log(Exception exception, String msg) {

        log(msg, exception);

    }

    public void log(String msg, Throwable throwable) {

        CharArrayWriter buf = new CharArrayWriter();
        PrintWriter writer = new PrintWriter(buf);
        writer.println(msg);
        throwable.printStackTrace(writer);
        Throwable rootCause = null;
        if (throwable instanceof LifecycleException)
            rootCause = ((LifecycleException) throwable).getThrowable();
        else if (throwable instanceof ServletException)
            rootCause = ((ServletException) throwable).getRootCause();
        if (rootCause != null) {
            writer.println("----- Root Cause -----");
            rootCause.printStackTrace(writer);
        }
        log(buf.toString());

    }

    public void log(String message, int verbosity) {

        /**
         * 如果verbosity比实例中的日志记录级别低才会记录日志
         */
        if (this.verbosity >= verbosity)
            log(message);

    }

    public void log(String message, Throwable throwable, int verbosity) {

        /**
         * 如果verbosity比实例中的日志记录级别低才会记录日志
         */
        if (this.verbosity >= verbosity)
            log(message, throwable);

    }

}
