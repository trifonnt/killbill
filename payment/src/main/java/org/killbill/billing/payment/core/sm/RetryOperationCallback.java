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

package org.killbill.billing.payment.core.sm;

import java.math.BigDecimal;
import java.util.Set;

import javax.annotation.Nullable;

import org.killbill.automaton.Operation.OperationCallback;
import org.killbill.automaton.OperationException;
import org.killbill.automaton.OperationResult;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.callcontext.DefaultCallContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.DirectPayment;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.core.DirectPaymentProcessor;
import org.killbill.billing.payment.core.ProcessorBase.WithAccountLockCallback;
import org.killbill.billing.payment.dispatcher.PluginDispatcher;
import org.killbill.billing.payment.retry.DefaultRetryPluginResult;
import org.killbill.billing.retry.plugin.api.RetryPluginApi;
import org.killbill.billing.retry.plugin.api.RetryPluginApi.RetryPluginContext;
import org.killbill.billing.retry.plugin.api.RetryPluginApi.RetryPluginResult;
import org.killbill.billing.retry.plugin.api.RetryPluginApiException;
import org.killbill.billing.retry.plugin.api.UnknownEntryException;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.commons.locker.GlobalLocker;

public abstract class RetryOperationCallback extends PluginOperation implements OperationCallback {

    protected final DirectPaymentProcessor directPaymentProcessor;
    private final OSGIServiceRegistration<RetryPluginApi> retryPluginRegistry;

    protected RetryOperationCallback(final GlobalLocker locker, final PluginDispatcher<OperationResult> paymentPluginDispatcher, final RetryableDirectPaymentStateContext directPaymentStateContext, final DirectPaymentProcessor directPaymentProcessor, final OSGIServiceRegistration<RetryPluginApi> retryPluginRegistry) {
        super(locker, paymentPluginDispatcher, directPaymentStateContext);
        this.directPaymentProcessor = directPaymentProcessor;
        this.retryPluginRegistry = retryPluginRegistry;
    }

    //
    // STEPH issue because externalKey namespace across plugin is not unique
    //
    private RetryPluginResult getPluginResult(@Nullable final String pluginName, final RetryPluginContext retryContext) throws RetryPluginApiException {

        if (pluginName != null) {
            final RetryPluginApi plugin = retryPluginRegistry.getServiceForName(pluginName);
            try {
                final RetryPluginResult result = plugin.getRetryResult(retryContext);
                return result;
            } catch (UnknownEntryException e) {
                return new DefaultRetryPluginResult(true);
            }
        }

        final Set<String> allServices = retryPluginRegistry.getAllServices();
        for (String cur : allServices) {
            final RetryPluginApi plugin = retryPluginRegistry.getServiceForName(cur);
            try {
                final RetryPluginResult result = plugin.getRetryResult(retryContext);
                if (!result.isAborted()) {
                    return result;
                }
            } catch (UnknownEntryException ignore) {
            }
        }
        return new DefaultRetryPluginResult(true);
    }

    @Override
    public OperationResult doOperationCallback() throws OperationException {
        return dispatchWithTimeout(new WithAccountLockCallback<OperationResult>() {
            @Override
            public OperationResult doOperation() throws OperationException {

                final RetryableDirectPaymentStateContext retryableDirectPaymentStateContext = (RetryableDirectPaymentStateContext) directPaymentStateContext;
                final DefaultPluginRetryContext retryContext = new DefaultPluginRetryContext(directPaymentStateContext.account,
                                                                                             directPaymentStateContext.getDirectPaymentExternalKey(),
                                                                                             directPaymentStateContext.directPaymentTransactionExternalKey,
                                                                                             directPaymentStateContext.transactionType,
                                                                                             directPaymentStateContext.amount,
                                                                                             directPaymentStateContext.currency,
                                                                                             directPaymentStateContext.properties,
                                                                                             retryableDirectPaymentStateContext.isApiPayment(),
                                                                                             directPaymentStateContext.callContext);

                // Note that we are using OperationResult.EXCEPTION result to transition to final ABORTED state -- see RetryStates.xml
                final RetryPluginResult pluginResult;
                try {
                    pluginResult = getPluginResult(retryableDirectPaymentStateContext.getPluginName(), retryContext);
                    if (pluginResult.isAborted()) {
                        return OperationResult.EXCEPTION;
                    }
                } catch (RetryPluginApiException e) {
                    throw new OperationException(e, OperationResult.EXCEPTION);
                }

                try {
                    // Adjust amount with value returned by plugin if necessary
                    if (directPaymentStateContext.getAmount() == null ||
                        (pluginResult.getAdjustedAmount() != null && pluginResult.getAdjustedAmount().compareTo(directPaymentStateContext.getAmount()) != 0)) {
                        ((RetryableDirectPaymentStateContext) directPaymentStateContext).setAmount(pluginResult.getAdjustedAmount());
                    }

                    final DirectPayment result = doPluginOperation();
                    ((RetryableDirectPaymentStateContext) directPaymentStateContext).setResult(result);
                } catch (PaymentApiException e) {
                    if (pluginResult.getNextRetryDate() == null) {
                        throw new OperationException(e, OperationResult.EXCEPTION);
                    } else {
                        ((RetryableDirectPaymentStateContext) directPaymentStateContext).setRetryDate(pluginResult.getNextRetryDate());
                        throw new OperationException(e, OperationResult.FAILURE);
                    }
                } catch (Exception e) {
                    // STEPH_RETRY this will abort the retry logic, is that really what we want?
                    throw new OperationException(e, OperationResult.EXCEPTION);
                }
                return OperationResult.SUCCESS;
            }
        });
    }

    public class DefaultPluginRetryContext extends DefaultCallContext implements RetryPluginContext {

        private final Account account;
        private final String paymentExternalKey;
        private final String transactionExternalKey;
        private final TransactionType transactionType;
        private final BigDecimal amount;
        private final Currency currency;
        private final boolean isApiPayment;
        private final Iterable<PluginProperty> properties;

        public DefaultPluginRetryContext(final Account account, final String paymentExternalKey, final String transactionExternalKey, final TransactionType transactionType, final BigDecimal amount, final Currency currency, final Iterable<PluginProperty> properties, final boolean isApiPayment, final CallContext callContext) {
            super(callContext.getTenantId(), callContext.getUserName(), callContext.getCallOrigin(), callContext.getUserType(), callContext.getReasonCode(), callContext.getComments(), callContext.getUserToken(), callContext.getCreatedDate(), callContext.getUpdatedDate());
            this.account = account;
            this.paymentExternalKey = paymentExternalKey;
            this.transactionExternalKey = transactionExternalKey;
            this.transactionType = transactionType;
            this.amount = amount;
            this.currency = currency;
            this.properties = properties;
            this.isApiPayment = isApiPayment;
        }

        @Override
        public Account getAccount() {
            return account;
        }

        @Override
        public String getPaymentExternalKey() {
            return paymentExternalKey;
        }

        @Override
        public String getTransactionExternalKey() {
            return transactionExternalKey;
        }

        @Override
        public TransactionType getTransactionType() {
            return transactionType;
        }

        @Override
        public BigDecimal getAmount() {
            return amount;
        }

        @Override
        public Currency getCurrency() {
            return currency;
        }

        @Override
        public boolean isApiPayment() {
            return isApiPayment;
        }

        @Override
        public Iterable<PluginProperty> getPluginProperties() {
            return properties;
        }
    }
}
