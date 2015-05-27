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

package org.killbill.billing.payment.provider;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.payment.api.PaymentMethodPlugin;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.plugin.api.GatewayNotification;
import org.killbill.billing.payment.plugin.api.HostedPaymentPageFormDescriptor;
import org.killbill.billing.payment.plugin.api.NoOpPaymentPluginApi;
import org.killbill.billing.payment.plugin.api.PaymentMethodInfoPlugin;
import org.killbill.billing.payment.plugin.api.PaymentPluginApiException;
import org.killbill.billing.payment.plugin.api.PaymentPluginStatus;
import org.killbill.billing.payment.plugin.api.PaymentTransactionInfoPlugin;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.entity.DefaultPagination;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.clock.Clock;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;

public class DefaultNoOpPaymentProviderPlugin implements NoOpPaymentPluginApi {

    private static final String PLUGIN_NAME = "__NO_OP__";

    private final AtomicBoolean makeNextInvoiceFailWithError = new AtomicBoolean(false);
    private final AtomicBoolean makeNextInvoiceFailWithException = new AtomicBoolean(false);
    private final AtomicBoolean makeAllInvoicesFailWithError = new AtomicBoolean(false);

    private final Map<String, List<PaymentTransactionInfoPlugin>> payments = new ConcurrentHashMap<String, List<PaymentTransactionInfoPlugin>>();
    private final Map<String, List<PaymentMethodPlugin>> paymentMethods = new ConcurrentHashMap<String, List<PaymentMethodPlugin>>();

    private final Clock clock;

    @Inject
    public DefaultNoOpPaymentProviderPlugin(final Clock clock) {
        this.clock = clock;
        clear();
    }

    @Override
    public void clear() {
        makeNextInvoiceFailWithException.set(false);
        makeAllInvoicesFailWithError.set(false);
        makeNextInvoiceFailWithError.set(false);
    }

    @Override
    public void makeNextPaymentFailWithError() {
        makeNextInvoiceFailWithError.set(true);
    }

    @Override
    public void makeNextPaymentFailWithException() {
        makeNextInvoiceFailWithException.set(true);
    }

    @Override
    public void makeAllInvoicesFailWithError(final boolean failure) {
        makeAllInvoicesFailWithError.set(failure);
    }

    @Override
    public PaymentTransactionInfoPlugin authorizePayment(final UUID kbAccountId, final UUID kbPaymentId, final UUID kbTransactionId, final UUID kbPaymentMethodId, final BigDecimal amount, final Currency currency, final Iterable<PluginProperty> properties, final CallContext context)
            throws PaymentPluginApiException {
        return getInternalNoopPaymentInfoResult(kbPaymentId, kbTransactionId, TransactionType.AUTHORIZE, amount, currency);
    }

    @Override
    public PaymentTransactionInfoPlugin capturePayment(final UUID kbAccountId, final UUID kbPaymentId, final UUID kbTransactionId, final UUID kbPaymentMethodId, final BigDecimal amount, final Currency currency, final Iterable<PluginProperty> properties, final CallContext context)
            throws PaymentPluginApiException {
        return getInternalNoopPaymentInfoResult(kbPaymentId, kbTransactionId, TransactionType.CAPTURE, amount, currency);
    }

    @Override
    public PaymentTransactionInfoPlugin purchasePayment(final UUID kbAccountId, final UUID kbPaymentId, final UUID kbTransactionId, final UUID kbPaymentMethodId, final BigDecimal amount, final Currency currency, final Iterable<PluginProperty> properties, final CallContext context) throws PaymentPluginApiException {
        return getInternalNoopPaymentInfoResult(kbPaymentId, kbTransactionId, TransactionType.PURCHASE, amount, currency);
    }

    @Override
    public PaymentTransactionInfoPlugin voidPayment(final UUID kbAccountId, final UUID kbPaymentId, final UUID kbTransactionId, final UUID kbPaymentMethodId, final Iterable<PluginProperty> properties, final CallContext context)
            throws PaymentPluginApiException {
        return getInternalNoopPaymentInfoResult(kbPaymentId, kbTransactionId, TransactionType.VOID, BigDecimal.ZERO, null);
    }

    @Override
    public PaymentTransactionInfoPlugin creditPayment(final UUID kbAccountId, final UUID kbPaymentId, final UUID kbTransactionId, final UUID kbPaymentMethodId, final BigDecimal amount, final Currency currency, final Iterable<PluginProperty> properties, final CallContext context)
            throws PaymentPluginApiException {
        return getInternalNoopPaymentInfoResult(kbPaymentId, kbTransactionId, TransactionType.CREDIT, amount, currency);
    }

    @Override
    public List<PaymentTransactionInfoPlugin> getPaymentInfo(final UUID kbAccountId, final UUID kbPaymentId, final Iterable<PluginProperty> properties, final TenantContext context) throws PaymentPluginApiException {
        return payments.get(kbPaymentId.toString());
    }

    @Override
    public Pagination<PaymentTransactionInfoPlugin> searchPayments(final String searchKey, final Long offset, final Long limit, final Iterable<PluginProperty> properties, final TenantContext tenantContext) throws PaymentPluginApiException {

        final List<PaymentTransactionInfoPlugin> flattenedTransactions = ImmutableList.copyOf(Iterables.concat(payments.values()));
        final Collection<PaymentTransactionInfoPlugin> filteredTransactions = Collections2.<PaymentTransactionInfoPlugin>filter(flattenedTransactions,
                                                                                                                                new Predicate<PaymentTransactionInfoPlugin>() {
                                                                                                                                    @Override
                                                                                                                                    public boolean apply(final PaymentTransactionInfoPlugin input) {
                                                                                                                                        return (input.getKbPaymentId() != null && input.getKbPaymentId().toString().equals(searchKey)) ||
                                                                                                                                               (input.getFirstPaymentReferenceId() != null && input.getFirstPaymentReferenceId().contains(searchKey)) ||
                                                                                                                                               (input.getSecondPaymentReferenceId() != null && input.getSecondPaymentReferenceId().contains(searchKey));
                                                                                                                                    }
                                                                                                                                }
                                                                                                                               );

        final ImmutableList<PaymentTransactionInfoPlugin> allResults = ImmutableList.<PaymentTransactionInfoPlugin>copyOf(filteredTransactions);

        final List<PaymentTransactionInfoPlugin> results;
        if (offset >= allResults.size()) {
            results = ImmutableList.<PaymentTransactionInfoPlugin>of();
        } else if (offset + limit > allResults.size()) {
            results = allResults.subList(offset.intValue(), allResults.size());
        } else {
            results = allResults.subList(offset.intValue(), offset.intValue() + limit.intValue());
        }

        return new DefaultPagination<PaymentTransactionInfoPlugin>(offset, limit, (long) results.size(), (long) payments.values().size(), results.iterator());
    }

    @Override
    public void addPaymentMethod(final UUID kbAccountId, final UUID kbPaymentMethodId, final PaymentMethodPlugin paymentMethodProps, final boolean setDefault, final Iterable<PluginProperty> properties, final CallContext context) throws PaymentPluginApiException {
        final PaymentMethodPlugin realWithID = new DefaultNoOpPaymentMethodPlugin(kbPaymentMethodId, paymentMethodProps);
        List<PaymentMethodPlugin> pms = paymentMethods.get(kbPaymentMethodId.toString());
        if (pms == null) {
            pms = new LinkedList<PaymentMethodPlugin>();
            paymentMethods.put(kbPaymentMethodId.toString(), pms);
        }
        pms.add(realWithID);
    }

    @Override
    public void deletePaymentMethod(final UUID kbAccountId, final UUID kbPaymentMethodId, final Iterable<PluginProperty> properties, final CallContext context) throws PaymentPluginApiException {
        PaymentMethodPlugin toBeDeleted = null;
        final List<PaymentMethodPlugin> pms = paymentMethods.get(kbPaymentMethodId.toString());
        if (pms != null) {
            for (final PaymentMethodPlugin cur : pms) {
                if (cur.getExternalPaymentMethodId().equals(kbPaymentMethodId.toString())) {
                    toBeDeleted = cur;
                    break;
                }
            }
        }

        if (toBeDeleted != null) {
            pms.remove(toBeDeleted);
        }
    }

    @Override
    public PaymentMethodPlugin getPaymentMethodDetail(final UUID kbAccountId, final UUID kbPaymentMethodId, final Iterable<PluginProperty> properties, final TenantContext context) throws PaymentPluginApiException {
        final List<PaymentMethodPlugin> paymentMethodPlugins = paymentMethods.get(kbPaymentMethodId.toString());
        if (paymentMethodPlugins == null || paymentMethodPlugins.isEmpty()) {
            return null;
        } else {
            return paymentMethodPlugins.get(0);
        }
    }

    @Override
    public void setDefaultPaymentMethod(final UUID kbAccountId, final UUID kbPaymentMethodId, final Iterable<PluginProperty> properties, final CallContext context) throws PaymentPluginApiException {
    }

    @Override
    public List<PaymentMethodInfoPlugin> getPaymentMethods(final UUID kbAccountId, final boolean refreshFromGateway, final Iterable<PluginProperty> properties, final CallContext context) {
        return ImmutableList.<PaymentMethodInfoPlugin>of();
    }

    @Override
    public Pagination<PaymentMethodPlugin> searchPaymentMethods(final String searchKey, final Long offset, final Long limit, final Iterable<PluginProperty> properties, final TenantContext tenantContext) throws PaymentPluginApiException {
        final ImmutableList<PaymentMethodPlugin> allResults = ImmutableList.<PaymentMethodPlugin>copyOf(Iterables.<PaymentMethodPlugin>filter(Iterables.<PaymentMethodPlugin>concat(paymentMethods.values()), new Predicate<PaymentMethodPlugin>() {
            @Override
            public boolean apply(final PaymentMethodPlugin input) {
                return input.getKbPaymentMethodId().toString().equals(searchKey);
            }
        }));

        final List<PaymentMethodPlugin> results;
        if (offset >= allResults.size()) {
            results = ImmutableList.<PaymentMethodPlugin>of();
        } else if (offset + limit > allResults.size()) {
            results = allResults.subList(offset.intValue(), allResults.size());
        } else {
            results = allResults.subList(offset.intValue(), offset.intValue() + limit.intValue());
        }

        return new DefaultPagination<PaymentMethodPlugin>(offset, limit, (long) results.size(), (long) paymentMethods.values().size(), results.iterator());
    }

    @Override
    public void resetPaymentMethods(final UUID kbAccountId, final List<PaymentMethodInfoPlugin> paymentMethods, final Iterable<PluginProperty> properties, final CallContext callContext) {
    }

    @Override
    public HostedPaymentPageFormDescriptor buildFormDescriptor(final UUID kbAccountId, final Iterable<PluginProperty> customFields, final Iterable<PluginProperty> properties, final CallContext callContext) {
        return null;
    }

    @Override
    public GatewayNotification processNotification(final String notification, final Iterable<PluginProperty> properties, final CallContext callContext) throws PaymentPluginApiException {
        return null;
    }

    @Override
    public PaymentTransactionInfoPlugin refundPayment(final UUID kbAccountId, final UUID kbPaymentId, final UUID kbTransactionId, final UUID kbPaymentMethodId, final BigDecimal refundAmount, final Currency currency, final Iterable<PluginProperty> properties, final CallContext context) throws PaymentPluginApiException {
        final List<PaymentTransactionInfoPlugin> transactions = getPaymentInfo(kbAccountId, kbPaymentId, properties, context);
        if (transactions == null || transactions.size() == 0) {
            throw new PaymentPluginApiException("", String.format("No payment found for payment id %s (plugin %s)", kbPaymentId.toString(), PLUGIN_NAME));
        }

        final Iterable<PaymentTransactionInfoPlugin> refundTransactions = Iterables.filter(transactions, new Predicate<PaymentTransactionInfoPlugin>() {
            @Override
            public boolean apply(final PaymentTransactionInfoPlugin input) {
                return input.getTransactionType() == TransactionType.REFUND;
            }
        });

        BigDecimal maxAmountRefundable = BigDecimal.ZERO;
        for (PaymentTransactionInfoPlugin cur : refundTransactions) {
            maxAmountRefundable = maxAmountRefundable.add(cur.getAmount());
        }

        if (maxAmountRefundable.compareTo(refundAmount) < 0) {
            throw new PaymentPluginApiException("", String.format("Refund amount of %s for payment id %s is bigger than the payment amount %s (plugin %s)",
                                                                  refundAmount, kbPaymentId.toString(), maxAmountRefundable, PLUGIN_NAME));
        }
        return getInternalNoopPaymentInfoResult(kbPaymentId, kbTransactionId, TransactionType.REFUND, refundAmount, currency);
    }

    private PaymentTransactionInfoPlugin getInternalNoopPaymentInfoResult(final UUID kbPaymentId, final UUID kbTransactionId, final TransactionType transactionType, final BigDecimal amount, final Currency currency) throws PaymentPluginApiException {
        if (makeNextInvoiceFailWithException.getAndSet(false)) {
            throw new PaymentPluginApiException("", "test error");
        }

        final PaymentPluginStatus status = (makeAllInvoicesFailWithError.get() || makeNextInvoiceFailWithError.getAndSet(false)) ? PaymentPluginStatus.ERROR : PaymentPluginStatus.PROCESSED;
        BigDecimal totalAmount = amount;

        List<PaymentTransactionInfoPlugin> paymentTransactionInfoPlugins = payments.get(kbPaymentId.toString());
        if (paymentTransactionInfoPlugins == null) {
            paymentTransactionInfoPlugins = new ArrayList<PaymentTransactionInfoPlugin>();
            payments.put(kbPaymentId.toString(), paymentTransactionInfoPlugins);
        }
        final PaymentTransactionInfoPlugin result = new DefaultNoOpPaymentInfoPlugin(kbPaymentId, kbTransactionId, transactionType, totalAmount, currency, clock.getUTCNow(), clock.getUTCNow(), status, null);
        paymentTransactionInfoPlugins.add(result);
        return result;
    }
}
