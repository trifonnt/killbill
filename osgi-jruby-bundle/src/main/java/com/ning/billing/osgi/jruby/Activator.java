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

import java.util.HashMap;
import java.util.Map;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Activator implements BundleActivator {

    private static final Logger log = LoggerFactory.getLogger(Activator.class);

    private String pluginMainClass = null;
    private JRubyPlugin plugin = null;

    public void start(final BundleContext context) throws Exception {
        // TODO instantiate the plugin depending on the config

        // Validate and instantiate the plugin
        final Map<String, Object> killbillApis = retrieveKillbillApis(context);
        plugin.instantiatePlugin(killbillApis);

        log.info("Starting JRuby plugin {}", pluginMainClass);
        plugin.startPlugin();
    }

    public void stop(final BundleContext context) throws Exception {
        log.info("Stopping JRuby plugin {}", pluginMainClass);
        plugin.stopPlugin();
    }

    private Map<String, Object> retrieveKillbillApis(final BundleContext context) {
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

        return killbillUserApis;
    }
}
