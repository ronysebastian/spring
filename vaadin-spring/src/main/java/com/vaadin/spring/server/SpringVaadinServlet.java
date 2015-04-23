/*
 * Copyright 2015 The original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.vaadin.spring.server;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import com.vaadin.server.DefaultUIProvider;
import com.vaadin.server.DeploymentConfiguration;
import com.vaadin.server.ServiceException;
import com.vaadin.server.SessionInitEvent;
import com.vaadin.server.SessionInitListener;
import com.vaadin.server.UIProvider;
import com.vaadin.server.VaadinServlet;
import com.vaadin.server.VaadinServletRequest;
import com.vaadin.server.VaadinServletService;
import com.vaadin.server.VaadinSession;

/**
 * Subclass of the standard {@link com.vaadin.server.VaadinServlet Vaadin
 * servlet} that adds a {@link SpringUIProvider} to every new Vaadin session and
 * allows the use of a custom service URL on the bootstrap page.
 * <p>
 * If you need a custom Vaadin servlet, you can either extend this servlet
 * directly, or extend another subclass of {@link VaadinServlet} and just add
 * the UI provider.
 * <p>
 * This servlet also implements a hack to get around the behavior of Spring
 * ServletForwardingController/ServletWrappingController. Those controllers
 * return null as the pathInfo of requests forwarded to the Vaadin servlet, and
 * use the mapping as the servlet path whereas with Vaadin the mapping typically
 * corresponds to a UI, not a virtual servlet. Thus, there is an option to clear
 * the servlet path in requests and compute pathInfo accordingly. This is used
 * by Vaadin Spring Boot to make it easier to use Vaadin and Spring MVC
 * applications together in the same global "namespace".
 *
 * @author Petter Holmström (petter@vaadin.com)
 * @author Josh Long (josh@joshlong.com)
 */
public class SpringVaadinServlet extends VaadinServlet {

    private static final long serialVersionUID = 5371983676318947478L;

    private String serviceUrl = null;

    private boolean clearServletPath = false;

    @Override
    protected void servletInitialized() throws ServletException {
        getService().addSessionInitListener(new SessionInitListener() {

            private static final long serialVersionUID = -6307820453486668084L;

            @Override
            public void sessionInit(SessionInitEvent sessionInitEvent)
                    throws ServiceException {
                WebApplicationContext webApplicationContext = WebApplicationContextUtils
                        .getWebApplicationContext(getServletContext());

                // remove DefaultUIProvider instances to avoid mapping
                // extraneous UIs if e.g. a servlet is declared as a nested
                // class in a UI class
                VaadinSession session = sessionInitEvent.getSession();
                List<UIProvider> uiProviders = new ArrayList<UIProvider>(
                        session.getUIProviders());
                for (UIProvider provider : uiProviders) {
                    // use canonical names as these may have been loaded with
                    // different classloaders
                    if (DefaultUIProvider.class.getCanonicalName().equals(
                            provider.getClass().getCanonicalName())) {
                        session.removeUIProvider(provider);
                    }
                }

                // add Spring UI provider
                SpringUIProvider uiProvider = new SpringUIProvider(
                        webApplicationContext);
                session.addUIProvider(uiProvider);
            }
        });
    }

    /**
     * Return the service URL (URL for all client-server communication) to use
     * if customized, null for the default service URL.
     *
     * @return service URL or null to use the default
     */
    public String getServiceUrl() {
        return serviceUrl;
    }

    /**
     * Set the service URL (URL for all client-server communication) to use,
     * null to use the default service URL. The service URL must be set before
     * servlet service instances are created, i.e. before the servlet is placed
     * into service by the servlet container.
     *
     * @param serviceUrl
     *            to use or null for default
     */
    public void setServiceUrl(String serviceUrl) {
        this.serviceUrl = serviceUrl;
    }

    /**
     * Return true if always forcing the servlet path to be the empty string,
     * false otherwise.
     *
     * This API might change in future versions.
     *
     * @see SpringVaadinServlet
     *
     * @return true if clearing the servlet path
     */
    public boolean isClearServletPath() {
        return clearServletPath;
    }

    /**
     * Make the servlet use empty string as the servlet path (needed when using
     * ServletForwardingController). See the class level javadoc for
     * {@link SpringVaadinServlet} for more information.
     *
     * This API might change in future versions.
     *
     * @param clearServletPath
     *            true to use empty servlet path and have the full path as
     *            pathInfo, false not to remap the servlet path
     */
    public void setClearServletPath(boolean clearServletPath) {
        this.clearServletPath = clearServletPath;
    }

    @Override
    protected VaadinServletService createServletService(
            DeploymentConfiguration deploymentConfiguration)
            throws ServiceException {
        // this is needed when using a custom service URL
        SpringVaadinServletService service = new SpringVaadinServletService(
                this, deploymentConfiguration, getServiceUrl());
        service.init();
        return service;
    }

    @Override
    protected VaadinServletRequest createVaadinRequest(
            HttpServletRequest request) {
        return new SpringVaadinServletRequest(request, getService(),
                isClearServletPath());
    }

}
