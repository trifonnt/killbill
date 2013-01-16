/*
 * Copyright 2010-2013 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.ning.billing.osgi.jruby;

import java.util.Arrays;
import java.util.Map;

import org.jruby.Ruby;
import org.jruby.RubyObject;
import org.jruby.embed.EvalFailedException;
import org.jruby.embed.ScriptingContainer;

// Bridge between the OSGI bundle and the ruby plugin
public abstract class JRubyPlugin {

    // Killbill gem base classes
    private static final String KILLBILL_PLUGIN_BASE = "Killbill::Plugin::PluginBase";
    private static final String KILLBILL_PLUGIN_NOTIFICATION = "Killbill::Plugin::Notification";
    private static final String KILLBILL_PLUGIN_PAYMENT = "Killbill::Plugin::Payment";

    // Magic ruby variables
    private static final String JAVA_APIS = "java_apis";
    private static final String ACTIVE = "@active";

    protected final String pluginMainClass;
    protected final ScriptingContainer container;

    protected RubyObject pluginInstance;

    public JRubyPlugin(final String pluginMainClass, final String pluginLibdir) {
        this.pluginMainClass = pluginMainClass;
        this.container = new ScriptingContainer();

        // Path to the gem
        if (pluginLibdir != null) {
            container.setLoadPaths(Arrays.asList(pluginLibdir));
        }
    }

    public void instantiatePlugin(final Map<String, Object> killbillApis) {
        checkValidPlugin();

        // Register all killbill APIs
        container.put(JAVA_APIS, killbillApis);

        // Note that the JAVA_APIS variable will be available once only!
        // Don't put any code here!

        // Start the plugin
        pluginInstance = (RubyObject) container.runScriptlet(pluginMainClass + ".new(" + JAVA_APIS + ")");
    }

    public void startPlugin() {
        checkPluginIsStopped();
        pluginInstance.callMethod("start_plugin");
        checkPluginIsRunning();
    }

    public void stopPlugin() {
        checkPluginIsRunning();
        pluginInstance.callMethod("stop_plugin");
        checkPluginIsStopped();
    }

    protected void checkPluginIsRunning() {
        if (pluginInstance == null || !pluginInstance.getInstanceVariable(ACTIVE).isTrue()) {
            throw new IllegalStateException(String.format("Plugin %s didn't start properly", pluginMainClass));
        }
    }

    protected void checkPluginIsStopped() {
        if (pluginInstance == null || pluginInstance.getInstanceVariable(ACTIVE).isTrue()) {
            throw new IllegalStateException(String.format("Plugin %s didn't stop properly", pluginMainClass));
        }
    }

    protected void checkValidPlugin() {
        try {
            container.runScriptlet(checkInstanceOfPlugin(KILLBILL_PLUGIN_BASE));
        } catch (EvalFailedException e) {
            throw new IllegalArgumentException(e);
        }
    }

    protected void checkValidNotificationPlugin() throws IllegalArgumentException {
        try {
            container.runScriptlet(checkInstanceOfPlugin(KILLBILL_PLUGIN_NOTIFICATION));
        } catch (EvalFailedException e) {
            throw new IllegalArgumentException(e);
        }
    }

    protected void checkValidPaymentPlugin() throws IllegalArgumentException {
        try {
            container.runScriptlet(checkInstanceOfPlugin(KILLBILL_PLUGIN_PAYMENT));
        } catch (EvalFailedException e) {
            throw new IllegalArgumentException(e);
        }
    }

    protected String checkInstanceOfPlugin(final String baseClass) {
        return "require 'killbill'\n" +
               "raise ArgumentError.new('Invalid plugin: " + pluginMainClass + ", is not a " + baseClass + "') unless " + pluginMainClass + " <= " + baseClass;
    }

    protected Ruby getRuntime() {
        return pluginInstance.getMetaClass().getRuntime();
    }
}
