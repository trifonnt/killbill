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

package org.killbill.billing.payment.api.svcs;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import javax.inject.Inject;

import org.killbill.billing.account.api.Account;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.payment.api.DirectPayment;
import org.killbill.billing.payment.api.DirectPaymentApi;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.core.DirectPaymentProcessor;
import org.killbill.billing.payment.core.RetryableDirectPaymentProcessor;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.entity.Pagination;

public class RetryableDirectPaymentApi implements DirectPaymentApi {

    private final RetryableDirectPaymentProcessor retryableDirectPaymentProcessor;
    private final InternalCallContextFactory internalCallContextFactory;
    private final DirectPaymentProcessor directPaymentProcessor;

    @Inject
    public RetryableDirectPaymentApi(final DirectPaymentProcessor directPaymentProcessor,
                                     final RetryableDirectPaymentProcessor retryableDirectPaymentProcessor,
                                     final InternalCallContextFactory internalCallContextFactory) {
        this.directPaymentProcessor = directPaymentProcessor;
        this.retryableDirectPaymentProcessor = retryableDirectPaymentProcessor;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Override
    public DirectPayment createAuthorization(final Account account, final UUID paymentMethodId, final UUID directPaymentId, final BigDecimal amount, final Currency currency, final String directPaymentExternalKey, final String directPaymentTransactionExternalKey, final Iterable<PluginProperty> properties, final CallContext callContext) throws PaymentApiException {
        final InternalCallContext internalContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
        return retryableDirectPaymentProcessor.createAuthorization(account, paymentMethodId, directPaymentId, amount, currency, directPaymentExternalKey, directPaymentTransactionExternalKey, false, properties, callContext, internalContext);
    }

    @Override
    public DirectPayment createCapture(final Account account, final UUID directPaymentId, final BigDecimal amount, final Currency currency, final String directPaymentTransactionExternalKey, final Iterable<PluginProperty> properties, final CallContext callContext) throws PaymentApiException {
        final InternalCallContext internalContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
        return retryableDirectPaymentProcessor.createCapture(account, directPaymentId, amount, currency, directPaymentTransactionExternalKey, false, properties, callContext, internalContext);
    }

    @Override
    public DirectPayment createPurchase(final Account account, final UUID paymentMethodId, final UUID directPaymentId, final BigDecimal amount, final Currency currency, final String directPaymentExternalKey, final String directPaymentTransactionExternalKey, final Iterable<PluginProperty> properties, final CallContext callContext) throws PaymentApiException {
        final InternalCallContext internalContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
        return retryableDirectPaymentProcessor.createPurchase(account, paymentMethodId, directPaymentId, amount, currency, directPaymentExternalKey, directPaymentTransactionExternalKey, false, properties, callContext, internalContext);
    }

    @Override
    public DirectPayment createVoid(final Account account, final UUID directPaymentId, final String directPaymentTransactionExternalKey, final Iterable<PluginProperty> properties, final CallContext callContext) throws PaymentApiException {
        final InternalCallContext internalContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
        return retryableDirectPaymentProcessor.createVoid(account, directPaymentId, directPaymentTransactionExternalKey, false, properties, callContext, internalContext);
    }

    @Override
    public DirectPayment createRefund(final Account account, final UUID directPaymentId, final BigDecimal amount, final Currency currency, final String directPaymentTransactionExternalKey, final Iterable<PluginProperty> properties, final CallContext callContext) throws PaymentApiException {
        final InternalCallContext internalContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
        return retryableDirectPaymentProcessor.createRefund(account, directPaymentId, amount, currency, directPaymentTransactionExternalKey, false, properties, callContext, internalContext);
    }

    @Override
    public DirectPayment createCredit(final Account account, final UUID directPaymentId, final UUID uuid2, final BigDecimal amount, final Currency currency, final String directPaymentExternalKey, final String directPaymentTransactionExternalKey, final Iterable<PluginProperty> properties, final CallContext callContext) throws PaymentApiException {
        final InternalCallContext internalContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
        return retryableDirectPaymentProcessor.createCredit(account, directPaymentId, amount, currency, directPaymentExternalKey, directPaymentTransactionExternalKey, false, properties, callContext, internalContext);
    }

    @Override
    public List<DirectPayment> getAccountPayments(final UUID accountId, final TenantContext context) throws PaymentApiException {
        final InternalTenantContext internalContext = internalCallContextFactory.createInternalTenantContext(accountId, context);
        return directPaymentProcessor.getAccountPayments(accountId, internalContext);
    }

    @Override
    public DirectPayment getPayment(final UUID directPaymentId, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext context) throws PaymentApiException {
        final InternalTenantContext internalContext = internalCallContextFactory.createInternalTenantContext(context);
        return directPaymentProcessor.getPayment(directPaymentId, withPluginInfo, properties, context, internalContext);
    }

    @Override
    public Pagination<DirectPayment> getPayments(final Long offset, final Long limit, final Iterable<PluginProperty> properties, final TenantContext context) {
        final InternalTenantContext internalContext = internalCallContextFactory.createInternalTenantContext(context);
        return directPaymentProcessor.getPayments(offset, limit, properties, context, internalContext);
    }

    @Override
    public Pagination<DirectPayment> getPayments(final Long offset, final Long limit, final String pluginName, final Iterable<PluginProperty> properties, final TenantContext context) throws PaymentApiException {
        final InternalTenantContext internalContext = internalCallContextFactory.createInternalTenantContext(context);
        return directPaymentProcessor.getPayments(offset, limit, pluginName, properties, context, internalContext);
    }

    @Override
    public Pagination<DirectPayment> searchPayments(final String searchKey, final Long offset, final Long limit, final Iterable<PluginProperty> properties, final TenantContext context) {
        final InternalTenantContext internalContext = internalCallContextFactory.createInternalTenantContext(context);
        return directPaymentProcessor.searchPayments(searchKey, offset, limit, properties, context, internalContext);
    }

    @Override
    public Pagination<DirectPayment> searchPayments(final String searchKey, final Long offset, final Long limit, final String pluginName, final Iterable<PluginProperty> properties, final TenantContext context) throws PaymentApiException {
        final InternalTenantContext internalContext = internalCallContextFactory.createInternalTenantContext(context);
        return directPaymentProcessor.searchPayments(searchKey, offset, limit, pluginName, properties, context, internalContext);
    }
}
