/*
 * Copyright 2010-2012 Ning, Inc.
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

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jruby.Ruby;
import org.jruby.RubyObject;
import org.jruby.embed.EvalFailedException;
import org.jruby.embed.ScriptingContainer;
import org.jruby.javasupport.JavaEmbedUtils;
import org.jruby.runtime.ThreadContext;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.account.api.Account;
import com.ning.billing.beatrix.bus.api.ExtBusEvent;
import com.ning.billing.payment.api.PaymentMethodPlugin;
import com.ning.billing.payment.plugin.api.PaymentInfoPlugin;
import com.ning.billing.payment.plugin.api.PaymentPluginApi;
import com.ning.billing.payment.plugin.api.PaymentPluginApiException;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.TenantContext;

public class Activator implements BundleActivator, PaymentPluginApi {

    public static final String PLUGIN_LIBDIR_KEY = "com.ning.billing.killbill.osgi.jruby.plugin.dir";
    public static final String PLUGIN_MAIN_CLASS_KEY = "com.ning.billing.killbill.osgi.jruby.plugin.class";

    private static final Logger log = LoggerFactory.getLogger(Activator.class);

    // Killbill gem base classes
    private static final String KILLBILL_PLUGIN_BASE = "Killbill::Plugin::PluginBase";
    private static final String KILLBILL_PLUGIN_NOTIFICATION = "Killbill::Plugin::Notification";
    private static final String KILLBILL_PLUGIN_PAYMENT = "Killbill::Plugin::Payment";

    private static final String JAVA_APIS = "java_apis";
    private static final String ACTIVE = "@active";

    private final ScriptingContainer container = new ScriptingContainer();

    private RubyObject pluginInstance = null;
    private String pluginMainClass = null;

    // OSGI interface

    public void start(final BundleContext context) throws Exception {
        // Path to the gem
        final String pluginLibdir = context.getProperty(PLUGIN_LIBDIR_KEY);
        if (pluginLibdir != null) {
            container.setLoadPaths(Arrays.asList(pluginLibdir));
        }

        // Retrieve the plugin Ruby class
        pluginMainClass = context.getProperty(PLUGIN_MAIN_CLASS_KEY);

        // Validate and instantiate the plugin
        checkValidPlugin();
        instantiatePlugin(context);

        log.info("Starting JRuby plugin {}", pluginMainClass);
        startPlugin(context);
    }

    public void stop(final BundleContext context) throws Exception {
        log.info("Stopping JRuby plugin {}", pluginMainClass);
        stopPlugin(context);
    }

    // Notification plugins

    public void onEvent(final ExtBusEvent event) {
        checkValidNotificationPlugin();
        checkPluginIsRunning();

        pluginInstance.callMethod("on_event", JavaEmbedUtils.javaToRuby(getRuntime(), event));
    }

    // Payment plugins

    @Override
    public String getName() {
        return pluginMainClass;
    }

    @Override
    public PaymentInfoPlugin processPayment(final String externalAccountKey, final UUID paymentId, final BigDecimal amount, final CallContext context) throws PaymentPluginApiException {
        checkValidPaymentPlugin();
        checkPluginIsRunning();

        final Ruby runtime = getRuntime();
        pluginInstance.callMethod("charge",
                                  JavaEmbedUtils.javaToRuby(runtime, externalAccountKey),
                                  JavaEmbedUtils.javaToRuby(runtime, paymentId.toString()),
                                  JavaEmbedUtils.javaToRuby(runtime, amount.longValue() * 100));

        // TODO
        return null;
    }

    @Override
    public PaymentInfoPlugin getPaymentInfo(final UUID paymentId, final TenantContext context) throws PaymentPluginApiException {
        checkValidPaymentPlugin();
        checkPluginIsRunning();

        pluginInstance.callMethod("get_payment_info", JavaEmbedUtils.javaToRuby(getRuntime(), paymentId.toString()));

        // TODO
        return null;
    }

    @Override
    public void processRefund(final Account account, final UUID paymentId, final BigDecimal refundAmount, final CallContext context) throws PaymentPluginApiException {
        checkValidPaymentPlugin();
        checkPluginIsRunning();

        final Ruby runtime = getRuntime();
        pluginInstance.callMethod("refund",
                                  JavaEmbedUtils.javaToRuby(runtime, account.getExternalKey()),
                                  JavaEmbedUtils.javaToRuby(runtime, paymentId.toString()),
                                  JavaEmbedUtils.javaToRuby(runtime, refundAmount.longValue() * 100));
    }

    @Override
    public int getNbRefundForPaymentAmount(final Account account, final UUID paymentId, final BigDecimal refundAmount, final TenantContext context) throws PaymentPluginApiException {
        throw new UnsupportedOperationException();
    }

    @Override
    public String createPaymentProviderAccount(final Account account, final CallContext context) throws PaymentPluginApiException {
        checkValidPaymentPlugin();
        checkPluginIsRunning();

        pluginInstance.callMethod("create_account", JavaEmbedUtils.javaToRuby(getRuntime(), account));

        // TODO
        return null;
    }

    @Override
    public List<PaymentMethodPlugin> getPaymentMethodDetails(final String accountKey, final TenantContext context) throws PaymentPluginApiException {
        throw new UnsupportedOperationException();
    }

    @Override
    public PaymentMethodPlugin getPaymentMethodDetail(final String accountKey, final String externalPaymentMethodId, final TenantContext context) throws PaymentPluginApiException {
        throw new UnsupportedOperationException();
    }

    @Override
    public String addPaymentMethod(final String accountKey, final PaymentMethodPlugin paymentMethodProps, final boolean setDefault, final CallContext context) throws PaymentPluginApiException {
        checkValidPaymentPlugin();
        checkPluginIsRunning();

        final Ruby runtime = getRuntime();
        pluginInstance.callMethod("add_payment_method",
                                  JavaEmbedUtils.javaToRuby(runtime, accountKey),
                                  JavaEmbedUtils.javaToRuby(runtime, paymentMethodProps));
        if (setDefault) {
            setDefaultPaymentMethod(accountKey, paymentMethodProps.getExternalPaymentMethodId(), context);
        }

        // TODO
        return null;
    }

    @Override
    public void updatePaymentMethod(final String accountKey, final PaymentMethodPlugin paymentMethodProps, final CallContext context) throws PaymentPluginApiException {
        checkValidPaymentPlugin();
        checkPluginIsRunning();

        final Ruby runtime = getRuntime();
        pluginInstance.callMethod("update_payment_method",
                                  JavaEmbedUtils.javaToRuby(runtime, accountKey),
                                  JavaEmbedUtils.javaToRuby(runtime, paymentMethodProps));
    }

    @Override
    public void deletePaymentMethod(final String accountKey, final String externalPaymentMethodId, final CallContext context) throws PaymentPluginApiException {
        checkValidPaymentPlugin();
        checkPluginIsRunning();

        final Ruby runtime = getRuntime();
        pluginInstance.callMethod("delete_payment_method",
                                  JavaEmbedUtils.javaToRuby(runtime, accountKey),
                                  JavaEmbedUtils.javaToRuby(runtime, externalPaymentMethodId));
    }

    @Override
    public void setDefaultPaymentMethod(final String accountKey, final String externalPaymentMethodId, final CallContext context) throws PaymentPluginApiException {
        checkValidPaymentPlugin();
        checkPluginIsRunning();

        final Ruby runtime = getRuntime();
        pluginInstance.callMethod("set_default_payment_method",
                                  JavaEmbedUtils.javaToRuby(runtime, accountKey),
                                  JavaEmbedUtils.javaToRuby(runtime, externalPaymentMethodId));
    }

    // Private methods

    private void startPlugin(final BundleContext context) {
        checkPluginIsStopped();
        pluginInstance.callMethod("start_plugin");
        checkPluginIsRunning();
    }

    private void stopPlugin(final BundleContext context) {
        checkPluginIsRunning();
        pluginInstance.callMethod("stop_plugin");
        checkPluginIsStopped();
    }

    private void registerKillbillApis(final BundleContext context) {
        final Map<String, Object> killbillUserApis = new HashMap<String, Object>();

        // See killbill/plugin.rb for the naming convention magic
        killbillUserApis.put("account_user_api", null);
        killbillUserApis.put("analytics_sanity_api", null);
        killbillUserApis.put("analytics_user_api", null);
        killbillUserApis.put("catalog_user_api", null);
        killbillUserApis.put("entitlement_migration_api", null);
        killbillUserApis.put("entitlement_timeline_api", null);
        killbillUserApis.put("entitlement_transfer_api", null);
        killbillUserApis.put("entitlement_user_api", null);
        killbillUserApis.put("invoice_migration_api", null);
        killbillUserApis.put("invoice_payment_api", null);
        killbillUserApis.put("invoice_user_api", null);
        killbillUserApis.put("meter_user_api", null);
        killbillUserApis.put("overdue_user_api", null);
        killbillUserApis.put("payment_api", null);
        killbillUserApis.put("tenant_user_api", null);
        killbillUserApis.put("usage_user_api", null);
        killbillUserApis.put("audit_user_api", null);
        killbillUserApis.put("custom_field_user_api", null);
        killbillUserApis.put("export_user_api", null);
        killbillUserApis.put("tag_user_api", null);

        container.put(JAVA_APIS, killbillUserApis);
    }

    private void instantiatePlugin(final BundleContext context) {
        // Register all killbill APIs
        registerKillbillApis(context);

        // Note that the JAVA_APIS variable will be available once only!
        // Don't put any code here!

        // Start the plugin
        pluginInstance = (RubyObject) container.runScriptlet(pluginMainClass + ".new(" + JAVA_APIS + ")");
    }

    private void checkPluginIsRunning() {
        if (pluginInstance == null || !pluginInstance.getInstanceVariable(ACTIVE).isTrue()) {
            throw new IllegalStateException(String.format("Plugin %s didn't start properly", pluginMainClass));
        }
    }

    private void checkPluginIsStopped() {
        if (pluginInstance == null || pluginInstance.getInstanceVariable(ACTIVE).isTrue()) {
            throw new IllegalStateException(String.format("Plugin %s didn't stop properly", pluginMainClass));
        }
    }

    private void checkValidPlugin() {
        try {
            container.runScriptlet(checkInstanceOfPlugin(KILLBILL_PLUGIN_BASE));
        } catch (EvalFailedException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private void checkValidNotificationPlugin() throws IllegalArgumentException {
        try {
            container.runScriptlet(checkInstanceOfPlugin(KILLBILL_PLUGIN_NOTIFICATION));
        } catch (EvalFailedException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private void checkValidPaymentPlugin() throws IllegalArgumentException {
        try {
            container.runScriptlet(checkInstanceOfPlugin(KILLBILL_PLUGIN_PAYMENT));
        } catch (EvalFailedException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private String checkInstanceOfPlugin(final String baseClass) {
        return "require 'killbill'\n" +
               "raise ArgumentError.new('Invalid plugin: " + pluginMainClass + ", is not a " + baseClass + "') unless " + pluginMainClass + " <= " + baseClass;
    }

    private ThreadContext getCurrentContext() {
        return getRuntime().getCurrentContext();
    }

    private Ruby getRuntime() {
        return pluginInstance.getMetaClass().getRuntime();
    }
}
