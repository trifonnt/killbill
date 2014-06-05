/*
 * Copyright 2014 Groupon, Inc
 *
 * Groupon licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.payment.provider;

import org.joda.time.DateTime;
import org.killbill.billing.payment.retry.DefaultRetryPluginResult;
import org.killbill.billing.retry.plugin.api.RetryPluginApi;
import org.killbill.billing.retry.plugin.api.RetryPluginApiException;
import org.killbill.billing.retry.plugin.api.UnknownEntryException;

public class MockRetryProviderPlugin implements RetryPluginApi {

    public static final String PLUGIN_NAME = "MOCK_RETRY_PLUGIN";

    private boolean isAborted;
    private DateTime nextRetryDate;

    public MockRetryProviderPlugin setAborted(final boolean isAborted) {
        this.isAborted = isAborted;
        return this;
    }

    public MockRetryProviderPlugin setNextRetryDate(final DateTime nextRetryDate) {
        this.nextRetryDate = nextRetryDate;
        return this;
    }

    @Override
    public RetryPluginResult getPluginResult(final RetryPluginContext retryPluginContext) throws RetryPluginApiException, UnknownEntryException {
        return new DefaultRetryPluginResult(isAborted, null);
    }

    @Override
    public DateTime getNextRetryDate(final RetryPluginContext retryPluginContext) throws RetryPluginApiException, UnknownEntryException {
        return null;
    }
}
