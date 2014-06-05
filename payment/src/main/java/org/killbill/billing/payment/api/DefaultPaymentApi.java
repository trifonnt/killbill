/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.payment.api;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceInternalApi;
import org.killbill.billing.invoice.api.InvoicePayment;
import org.killbill.billing.osgi.api.OSGIServiceDescriptor;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.core.DirectPaymentProcessor;
import org.killbill.billing.payment.core.PaymentMethodProcessor;
import org.killbill.billing.payment.core.sm.RetryableDirectPaymentAutomatonRunner;
import org.killbill.billing.payment.dao.DirectPaymentModelDao;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.dao.PaymentModelDao;
import org.killbill.billing.payment.retry.InvoiceRetryPluginApi;
import org.killbill.billing.retry.plugin.api.RetryPluginApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.config.PaymentConfig;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.clock.Clock;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;

public class DefaultPaymentApi implements PaymentApi {

    private final PaymentMethodProcessor methodProcessor;
    private final InternalCallContextFactory internalCallContextFactory;
    private final PaymentDao paymentDao;

    private final RetryableDirectPaymentAutomatonRunner retryableDirectPaymentAutomatonRunner;
    private final DirectPaymentProcessor directPaymentProcessor;
    private final InvoiceInternalApi invoiceApi;

    @Inject
    public DefaultPaymentApi(final PaymentMethodProcessor methodProcessor,
                             final InvoiceInternalApi invoiceApi,
                             final Clock clock,
                             final RetryableDirectPaymentAutomatonRunner retryableDirectPaymentAutomatonRunner,
                             final DirectPaymentProcessor directPaymentProcessor,
                             final InternalCallContextFactory internalCallContextFactory,
                             final PaymentConfig paymentConfig,
                             final PaymentDao paymentDao,
                             final OSGIServiceRegistration<RetryPluginApi> retryPluginRegistry) {
        this.methodProcessor = methodProcessor;
        this.retryableDirectPaymentAutomatonRunner = retryableDirectPaymentAutomatonRunner;
        this.directPaymentProcessor = directPaymentProcessor;
        this.internalCallContextFactory = internalCallContextFactory;
        this.paymentDao = paymentDao;
        this.invoiceApi = invoiceApi;

        final OSGIServiceDescriptor desc = new OSGIServiceDescriptor() {
            @Override
            public String getPluginSymbolicName() {
                return null;
            }

            @Override
            public String getRegistrationName() {
                return InvoiceRetryPluginApi.PLUGIN_NAME;
            }
        };
        retryPluginRegistry.registerService(desc, new InvoiceRetryPluginApi(paymentConfig, invoiceApi, paymentDao, internalCallContextFactory, clock));

    }

    @Override
    public DirectPayment createPayment(final Account account, final UUID invoiceId,
                                       @Nullable final BigDecimal amount, final Iterable<PluginProperty> properties, final CallContext context) throws PaymentApiException {

        final InternalCallContext internalContext = internalCallContextFactory.createInternalCallContext(account.getId(), context);
        final DirectPayment directPayment = retryableDirectPaymentAutomatonRunner.run(true,
                                                                                      TransactionType.PURCHASE,
                                                                                      account,
                                                                                      account.getPaymentMethodId(),
                                                                                      null,
                                                                                      invoiceId.toString(),
                                                                                      UUID.randomUUID().toString(),
                                                                                      amount,
                                                                                      account.getCurrency(),
                                                                                      false,
                                                                                      ImmutableMap.<UUID, BigDecimal>of(),
                                                                                      properties,
                                                                                      InvoiceRetryPluginApi.PLUGIN_NAME,
                                                                                      context,
                                                                                      internalContext);

        notifyOfPayment(invoiceId, internalContext, directPayment);
        return directPayment;
    }

    @Override
    public DirectPayment createExternalPayment(final Account account, final UUID invoiceId, final BigDecimal amount, final CallContext context) throws PaymentApiException {
        final InternalCallContext internalContext = internalCallContextFactory.createInternalCallContext(account.getId(), context);
        final DirectPayment directPayment = retryableDirectPaymentAutomatonRunner.run(true,
                                                                                      TransactionType.PURCHASE,
                                                                                      account,
                                                                                      account.getPaymentMethodId(),
                                                                                      null,
                                                                                      invoiceId.toString(),
                                                                                      UUID.randomUUID().toString(),
                                                                                      amount,
                                                                                      account.getCurrency(),
                                                                                      true,
                                                                                      ImmutableMap.<UUID, BigDecimal>of(),
                                                                                      ImmutableList.<PluginProperty>of(),
                                                                                      InvoiceRetryPluginApi.PLUGIN_NAME,
                                                                                      context,
                                                                                      internalContext);

        // STEPH should we really implement a state machine of top of that, or if not how do we handle errors.
        notifyOfPayment(invoiceId, internalContext, directPayment);
        return directPayment;
    }

    private void notifyOfPayment(final UUID invoiceId, final InternalCallContext internalContext, final DirectPayment directPayment) throws PaymentApiException {
        try {
            invoiceApi.notifyOfPayment(invoiceId,
                                       directPayment.getPurchasedAmount(),
                                       directPayment.getCurrency(),
                                       directPayment.getCurrency(),
                                       directPayment.getId(),
                                       directPayment.getCreatedDate(),
                                       internalContext);
        } catch (InvoiceApiException e) {
            throw new PaymentApiException(e);
        }
    }

    @Override
    public void notifyPendingPaymentOfStateChanged(final Account account, final UUID paymentId, final boolean isSuccess, final CallContext context) throws PaymentApiException {
        // STEPH should that even be part of this API?
/*
        paymentProcessor.notifyPendingPaymentOfStateChanged(account, paymentId, isSuccess,
                                                            internalCallContextFactory.createInternalCallContext(account.getId(), context));
                                                            */
    }

    @Override
    public void notifyPaymentOfChargeback(final Account account, final UUID paymentId, final BigDecimal amount, final Currency currency, final CallContext callContext) throws PaymentApiException {
        // STEPH TODO Implement when merging with DirectPaymentApi
    }

    @Override
    public DirectPayment retryPayment(final Account account, final UUID paymentId, final Iterable<PluginProperty> properties, final CallContext context) throws PaymentApiException {
        // STEPH to be implemented...
        /*
        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), context);
        paymentProcessor.retryPaymentFromApi(paymentId, properties, context, internalCallContext);
        return getPayment(paymentId, false, properties, context);
        */
        return null;
    }

    @Override
    public Pagination<DirectPayment> getPayments(final Long offset, final Long limit, final Iterable<PluginProperty> properties, final TenantContext tenantContext) {
        return directPaymentProcessor.getPayments(offset, limit, properties, tenantContext, internalCallContextFactory.createInternalTenantContext(tenantContext));
    }

    @Override
    public Pagination<DirectPayment> getPayments(final Long offset, final Long limit, final String pluginName, final Iterable<PluginProperty> properties, final TenantContext tenantContext) throws PaymentApiException {
        return directPaymentProcessor.getPayments(offset, limit, pluginName, properties, tenantContext, internalCallContextFactory.createInternalTenantContext(tenantContext));
    }

    @Override
    public DirectPayment getPayment(final UUID paymentId, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext tenantContext) throws PaymentApiException {
        return directPaymentProcessor.getPayment(paymentId, withPluginInfo, properties, tenantContext, internalCallContextFactory.createInternalTenantContext(tenantContext));
    }

    @Override
    public Pagination<DirectPayment> searchPayments(final String searchKey, final Long offset, final Long limit, final Iterable<PluginProperty> properties, final TenantContext context) {
        return directPaymentProcessor.searchPayments(searchKey, offset, limit, properties, context, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public Pagination<DirectPayment> searchPayments(final String searchKey, final Long offset, final Long limit, final String pluginName, final Iterable<PluginProperty> properties, final TenantContext context) throws PaymentApiException {
        return directPaymentProcessor.searchPayments(searchKey, offset, limit, pluginName, properties, context, internalCallContextFactory.createInternalTenantContext(context));
    }


    //
    // STEPH how different those search are from the one above, and do we really need both APIs -- since we always return  DirectPayment anyway?
    //
    @Override
    public Pagination<DirectPayment> getRefunds(final Long offset, final Long limit, final Iterable<PluginProperty> properties, final TenantContext context) {
        //return refundProcessor.getRefunds(offset, limit, properties, context, internalCallContextFactory.createInternalTenantContext(context));
        return null;
    }

    @Override
    public Pagination<DirectPayment> getRefunds(final Long offset, final Long limit, final String pluginName, final Iterable<PluginProperty> properties, final TenantContext tenantContext) throws PaymentApiException {
        //return refundProcessor.getRefunds(offset, limit, pluginName, properties, tenantContext, internalCallContextFactory.createInternalTenantContext(tenantContext));
        return null;
    }

    @Override
    public Pagination<DirectPayment> searchRefunds(final String searchKey, final Long offset, final Long limit, final Iterable<PluginProperty> properties, final TenantContext context) {
        //return refundProcessor.searchRefunds(searchKey, offset, limit, properties, context, internalCallContextFactory.createInternalTenantContext(context));
        return null;
    }

    @Override
    public Pagination<DirectPayment> searchRefunds(final String searchKey, final Long offset, final Long limit, final String pluginName, final Iterable<PluginProperty> properties, final TenantContext context) throws PaymentApiException {
        //return refundProcessor.searchRefunds(searchKey, offset, limit, pluginName, properties, context, internalCallContextFactory.createInternalTenantContext(context));
        return null;
    }

    @Override
    public List<DirectPayment> getInvoicePayments(final UUID invoiceId, final TenantContext context) {
        final InternalTenantContext internalContext = internalCallContextFactory.createInternalTenantContext(context);

        final List<PaymentModelDao> paymentsForInvoices = paymentDao.getPaymentsForInvoice(invoiceId, internalContext);
        final List<DirectPayment> result = new ArrayList<DirectPayment>();
        for (PaymentModelDao cur : paymentsForInvoices) {
            result.add(directPaymentProcessor.toDirectPayment(cur.getId(), null, internalContext));
        }
        return result;
    }

    @Override
    public List<DirectPayment> getAccountPayments(final UUID accountId, final TenantContext context)
            throws PaymentApiException {
        return directPaymentProcessor.getAccountPayments(accountId, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public DirectPayment getRefund(final UUID paymentid, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext tenantContext) throws PaymentApiException {
        // STEPH No more RefundId. Use paymentId; do we still need the API?
        return directPaymentProcessor.getPayment(paymentid, withPluginInfo, properties, tenantContext, internalCallContextFactory.createInternalTenantContext(tenantContext));
    }

    @Override
    public DirectPayment createRefund(final Account account, final UUID paymentId, final BigDecimal refundAmount, final Iterable<PluginProperty> properties, final CallContext context) throws PaymentApiException {
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentApiException(ErrorCode.PAYMENT_REFUND_AMOUNT_NEGATIVE_OR_NULL);
        }

        final InternalCallContext internalContext = internalCallContextFactory.createInternalCallContext(account.getId(), context);
        final DirectPayment directPayment = retryableDirectPaymentAutomatonRunner.run(true,
                                                                                      TransactionType.PURCHASE,
                                                                                      account,
                                                                                      account.getPaymentMethodId(),
                                                                                      paymentId,
                                                                                      null,
                                                                                      UUID.randomUUID().toString(),
                                                                                      refundAmount,
                                                                                      account.getCurrency(),
                                                                                      true,
                                                                                      ImmutableMap.<UUID, BigDecimal>of(),
                                                                                      ImmutableList.<PluginProperty>of(),
                                                                                      InvoiceRetryPluginApi.PLUGIN_NAME,
                                                                                      context,
                                                                                      internalContext);

        notifyOfRefund(paymentId, refundAmount, false, ImmutableMap.<UUID, BigDecimal>of(), internalContext);
        return directPayment;
    }

    @Override
    public void notifyPendingRefundOfStateChanged(final Account account, final UUID refundId, final boolean isSuccess, final CallContext context) throws PaymentApiException {
        /*
        refundProcessor.notifyPendingRefundOfStateChanged(account, refundId, isSuccess,
                                                          internalCallContextFactory.createInternalCallContext(account.getId(), context));
                                                          */
    }

    @Override
    public DirectPayment createRefundWithAdjustment(final Account account, final UUID paymentId, final BigDecimal refundAmount, final Iterable<PluginProperty> properties, final CallContext context) throws PaymentApiException {
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentApiException(ErrorCode.PAYMENT_REFUND_AMOUNT_NEGATIVE_OR_NULL);
        }

        final InternalCallContext internalContext = internalCallContextFactory.createInternalCallContext(account.getId(), context);
        final String directPaymentTransactionExternalKey = UUID.randomUUID().toString();
        final DirectPayment directPayment = retryableDirectPaymentAutomatonRunner.run(true,
                                                                                      TransactionType.PURCHASE,
                                                                                      account,
                                                                                      account.getPaymentMethodId(),
                                                                                      paymentId,
                                                                                      null,
                                                                                      directPaymentTransactionExternalKey,
                                                                                      refundAmount,
                                                                                      account.getCurrency(),
                                                                                      true,
                                                                                      ImmutableMap.<UUID, BigDecimal>of(),
                                                                                      ImmutableList.<PluginProperty>of(),
                                                                                      InvoiceRetryPluginApi.PLUGIN_NAME,
                                                                                      context,
                                                                                      internalContext);

        notifyOfRefund(paymentId, refundAmount, false, ImmutableMap.<UUID, BigDecimal>of(), internalContext);
        return directPayment;
    }

    private InvoicePayment notifyOfRefund(final UUID paymentId, final BigDecimal refundAmount, final boolean isAdjusted, final Map<UUID, BigDecimal> idsWithAmounts, final InternalCallContext internalContext) throws PaymentApiException {
        try {
            // STEPH note that we now pass the paymentID and not the refund ID as there will be no more any refund table
            return invoiceApi.createRefund(paymentId, refundAmount, isAdjusted, idsWithAmounts, paymentId, internalContext);
        } catch (InvoiceApiException e) {
            throw new PaymentApiException(e);
        }
    }

    @Override
    public DirectPayment createRefundWithItemsAdjustments(final Account account, final UUID paymentId, final Set<UUID> invoiceItemIds, final Iterable<PluginProperty> properties, final CallContext context) throws PaymentApiException {
        final Map<UUID, BigDecimal> invoiceItemIdsWithAmounts = new HashMap<UUID, BigDecimal>();
        for (final UUID invoiceItemId : invoiceItemIds) {
            invoiceItemIdsWithAmounts.put(invoiceItemId, null);
        }
        return createRefundWithItemsAdjustments(account, paymentId, invoiceItemIdsWithAmounts, properties, context);
    }

    @Override
    public DirectPayment createRefundWithItemsAdjustments(final Account account, final UUID paymentId, final Map<UUID, BigDecimal> invoiceItemIdsWithAmounts, final Iterable<PluginProperty> properties, final CallContext context) throws PaymentApiException {

        final InternalCallContext internalContext = internalCallContextFactory.createInternalCallContext(account.getId(), context);
        final String directPaymentTransactionExternalKey = UUID.randomUUID().toString();

        final DirectPayment directPayment = retryableDirectPaymentAutomatonRunner.run(true,
                                                                                      TransactionType.PURCHASE,
                                                                                      account,
                                                                                      account.getPaymentMethodId(),
                                                                                      paymentId,
                                                                                      null,
                                                                                      directPaymentTransactionExternalKey,
                                                                                      null,
                                                                                      account.getCurrency(),
                                                                                      true,
                                                                                      invoiceItemIdsWithAmounts,
                                                                                      ImmutableList.<PluginProperty>of(),
                                                                                      InvoiceRetryPluginApi.PLUGIN_NAME,
                                                                                      context,
                                                                                      internalContext);

        final DirectPaymentTransaction refundTransaction = Iterables.find(directPayment.getTransactions(), new Predicate<DirectPaymentTransaction>() {
            @Override
            public boolean apply(final DirectPaymentTransaction input) {
                return input.getExternalKey().equals(directPaymentTransactionExternalKey);
            }
        });
        notifyOfRefund(paymentId, refundTransaction.getAmount(), true, invoiceItemIdsWithAmounts, internalContext);
        return directPayment;
    }

    @Override
    public List<DirectPayment> getAccountRefunds(final Account account, final TenantContext context)
            throws PaymentApiException {

        final InternalTenantContext internalContext = internalCallContextFactory.createInternalTenantContext(account.getId(), context);
        final List<DirectPaymentModelDao> payments = paymentDao.getDirectPaymentsForAccount(account.getId(), internalContext);
        final List<DirectPayment> result = new ArrayList<DirectPayment>();
        for (DirectPaymentModelDao cur : payments) {
            result.add(directPaymentProcessor.toDirectPayment(cur.getId(), null, internalContext));
        }
        return result;
    }

    // STEPH API should be changed as this is not a list anymore...
    @Override
    public List<DirectPayment> getPaymentRefunds(final UUID paymentId, final TenantContext context)
            throws PaymentApiException {

        final InternalTenantContext internalContext = internalCallContextFactory.createInternalTenantContext(context);
        return ImmutableList.<DirectPayment>of(directPaymentProcessor.toDirectPayment(paymentId, null, internalContext));
    }

    @Override
    public Set<String> getAvailablePlugins() {
        return methodProcessor.getAvailablePlugins();
    }

    @Override
    public UUID addPaymentMethod(final String pluginName, final Account account,
                                 final boolean setDefault, final PaymentMethodPlugin paymentMethodInfo,
                                 final Iterable<PluginProperty> properties, final CallContext context)
            throws PaymentApiException {
        return methodProcessor.addPaymentMethod(pluginName, account, setDefault, paymentMethodInfo, properties,
                                                context, internalCallContextFactory.createInternalCallContext(account.getId(), context));
    }

    @Override
    public List<PaymentMethod> getPaymentMethods(final Account account, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext context)
            throws PaymentApiException {
        return methodProcessor.getPaymentMethods(account, withPluginInfo, properties, context, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public PaymentMethod getPaymentMethodById(final UUID paymentMethodId, final boolean includedDeleted, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext context)
            throws PaymentApiException {
        return methodProcessor.getPaymentMethodById(paymentMethodId, includedDeleted, withPluginInfo, properties, context, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public Pagination<PaymentMethod> getPaymentMethods(final Long offset, final Long limit, final Iterable<PluginProperty> properties, final TenantContext context) {
        return methodProcessor.getPaymentMethods(offset, limit, properties, context, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public Pagination<PaymentMethod> getPaymentMethods(final Long offset, final Long limit, final String pluginName, final Iterable<PluginProperty> properties, final TenantContext context) throws PaymentApiException {
        return methodProcessor.getPaymentMethods(offset, limit, pluginName, properties, context, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public Pagination<PaymentMethod> searchPaymentMethods(final String searchKey, final Long offset, final Long limit, final Iterable<PluginProperty> properties, final TenantContext context) {
        return methodProcessor.searchPaymentMethods(searchKey, offset, limit, properties, context, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public Pagination<PaymentMethod> searchPaymentMethods(final String searchKey, final Long offset, final Long limit, final String pluginName, final Iterable<PluginProperty> properties, final TenantContext context) throws PaymentApiException {
        return methodProcessor.searchPaymentMethods(searchKey, offset, limit, pluginName, properties, context, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public void deletedPaymentMethod(final Account account, final UUID paymentMethodId, final boolean deleteDefaultPaymentMethodWithAutoPayOff, final Iterable<PluginProperty> properties, final CallContext context)
            throws PaymentApiException {
        methodProcessor.deletedPaymentMethod(account, paymentMethodId, deleteDefaultPaymentMethodWithAutoPayOff, properties, context, internalCallContextFactory.createInternalCallContext(account.getId(), context));
    }

    @Override
    public void setDefaultPaymentMethod(final Account account, final UUID paymentMethodId, final Iterable<PluginProperty> properties, final CallContext context)
            throws PaymentApiException {
        methodProcessor.setDefaultPaymentMethod(account, paymentMethodId, properties, context, internalCallContextFactory.createInternalCallContext(account.getId(), context));
    }

    @Override
    public List<PaymentMethod> refreshPaymentMethods(final String pluginName, final Account account, final Iterable<PluginProperty> properties, final CallContext context)
            throws PaymentApiException {
        return methodProcessor.refreshPaymentMethods(pluginName, account, properties, context, internalCallContextFactory.createInternalCallContext(account.getId(), context));
    }

    @Override
    public List<PaymentMethod> refreshPaymentMethods(final Account account, final Iterable<PluginProperty> properties, final CallContext context)
            throws PaymentApiException {
        final InternalCallContext callContext = internalCallContextFactory.createInternalCallContext(account.getId(), context);

        final List<PaymentMethod> paymentMethods = new LinkedList<PaymentMethod>();
        for (final String pluginName : methodProcessor.getAvailablePlugins()) {
            paymentMethods.addAll(methodProcessor.refreshPaymentMethods(pluginName, account, properties, context, callContext));
        }

        return paymentMethods;
    }

}
