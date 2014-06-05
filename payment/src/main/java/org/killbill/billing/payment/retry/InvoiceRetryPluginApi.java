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

package org.killbill.billing.payment.retry;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceInternalApi;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.payment.api.PaymentStatus;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.dao.DirectPaymentModelDao;
import org.killbill.billing.payment.dao.DirectPaymentTransactionModelDao;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.retry.plugin.api.RetryPluginApi;
import org.killbill.billing.retry.plugin.api.RetryPluginApiException;
import org.killbill.billing.retry.plugin.api.UnknownEntryException;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.config.PaymentConfig;
import org.killbill.clock.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public final class InvoiceRetryPluginApi implements RetryPluginApi {

    public final static String PLUGIN_NAME = "__INVOICE_RETRY_PLUGIN__";

    private final PaymentConfig paymentConfig;
    private final InvoiceInternalApi invoiceApi;
    private final PaymentDao paymentDao;
    private final InternalCallContextFactory internalCallContextFactory;
    private final Clock clock;

    private final Logger logger = LoggerFactory.getLogger(InvoiceRetryPluginApi.class);

    public InvoiceRetryPluginApi(final PaymentConfig paymentConfig, final InvoiceInternalApi invoiceApi, final PaymentDao paymentDao,
                                 final InternalCallContextFactory internalCallContextFactory, final Clock clock) {
        this.paymentConfig = paymentConfig;
        this.invoiceApi = invoiceApi;
        this.paymentDao = paymentDao;
        this.internalCallContextFactory = internalCallContextFactory;
        this.clock = clock;
    }

    @Override
    public RetryPluginResult getPluginResult(final RetryPluginContext retryPluginContext) throws RetryPluginApiException {

        final InternalCallContext internalContext = internalCallContextFactory.createInternalCallContext(retryPluginContext.getAccount().getId(), retryPluginContext);
        switch (retryPluginContext.getTransactionType()) {
            case PURCHASE:
                return getPluginPurchaseResult(retryPluginContext, internalContext);

            case REFUND:
                return getPluginRefundResult(retryPluginContext, internalContext);

            default:
                throw new RuntimeException("Unsupported transaction type " + retryPluginContext.getTransactionType());
        }
    }

    @Override
    public DateTime getNextRetryDate(RetryPluginContext retryPluginContext)
            throws RetryPluginApiException {
        final InternalCallContext internalContext = internalCallContextFactory.createInternalCallContext(retryPluginContext.getAccount().getId(), retryPluginContext);
        switch (retryPluginContext.getTransactionType()) {
            case PURCHASE:
                return computeNextRetryDate(retryPluginContext.getPaymentExternalKey(), internalContext);
            default:
                return null;
        }
    }

    private RetryPluginResult getPluginPurchaseResult(final RetryPluginContext retryPluginContext, final InternalCallContext internalContext) throws RetryPluginApiException {
        final UUID invoiceId = UUID.fromString(retryPluginContext.getPaymentExternalKey());
        try {
            final Invoice invoice = rebalanceAndGetInvoice(invoiceId, internalContext);
            final BigDecimal requestedAmount = validateAndComputePaymentAmount(invoice, retryPluginContext.getAmount(), retryPluginContext.isApiPayment());
            final boolean isAborted = requestedAmount.compareTo(BigDecimal.ZERO) == 0;
            return new DefaultRetryPluginResult(isAborted, requestedAmount);
        } catch (InvoiceApiException e) {
            // Invoice is not known so return UnknownEntryException so caller knows whether or not it should try with other plugins
            throw new UnknownEntryException();
        }
    }

    private RetryPluginResult getPluginRefundResult(final RetryPluginContext retryPluginContext, final InternalCallContext internalContext) throws RetryPluginApiException {

        try {
            final DirectPaymentModelDao directPayment = paymentDao.getDirectPaymentByExternalKey(retryPluginContext.getPaymentExternalKey(), internalContext);
            if (directPayment == null) {
                throw new UnknownEntryException();
            }
            final BigDecimal amountToBeRefunded = computeRefundAmount(directPayment.getId(), retryPluginContext.getAmount(), retryPluginContext.getIdsWithAmount(), internalContext);
            final boolean isAborted = amountToBeRefunded.compareTo(BigDecimal.ZERO) == 0;
            return new DefaultRetryPluginResult(isAborted, amountToBeRefunded);
        } catch (InvoiceApiException e) {
            throw new UnknownEntryException();
        }
    }

    private BigDecimal computeRefundAmount(final UUID paymentId, @Nullable final BigDecimal specifiedRefundAmount,
                                           final Map<UUID, BigDecimal> invoiceItemIdsWithAmounts, final InternalTenantContext context)
            throws InvoiceApiException {
        final List<InvoiceItem> items = invoiceApi.getInvoiceForPaymentId(paymentId, context).getInvoiceItems();

        BigDecimal amountFromItems = BigDecimal.ZERO;
        for (final UUID itemId : invoiceItemIdsWithAmounts.keySet()) {
            amountFromItems = amountFromItems.add(Objects.firstNonNull(invoiceItemIdsWithAmounts.get(itemId),
                                                                       getAmountFromItem(items, itemId)));
        }

        // Sanity check: if some items were specified, then the sum should be equal to specified refund amount, if specified
        if (amountFromItems.compareTo(BigDecimal.ZERO) != 0 && specifiedRefundAmount != null && specifiedRefundAmount.compareTo(amountFromItems) != 0) {
            throw new IllegalArgumentException("You can't specify a refund amount that doesn't match the invoice items amounts");
        }

        return Objects.firstNonNull(specifiedRefundAmount, amountFromItems);
    }

    private BigDecimal getAmountFromItem(final List<InvoiceItem> items, final UUID itemId) {
        for (final InvoiceItem item : items) {
            if (item.getId().equals(itemId)) {
                return item.getAmount();
            }
        }
        // STEPH should we really throw IllegalArgumentException , and how will the state machine react?
        throw new IllegalArgumentException("Unable to find invoice item for id " + itemId);
    }

    private DateTime computeNextRetryDate(final String paymentExternalKey, final InternalCallContext internalContext) {
        final List<DirectPaymentTransactionModelDao> purchasedTransactions = getPurchasedTransactions(paymentExternalKey, internalContext);
        if (purchasedTransactions.size() == 0) {
            return null;
        }
        final DirectPaymentTransactionModelDao lastTransaction = purchasedTransactions.get(purchasedTransactions.size() - 1);
        switch (lastTransaction.getPaymentStatus()) {
            case PAYMENT_FAILURE:
                return getNextRetryDateForPaymentFailure(purchasedTransactions);

            case UNKNOWN:
            case PLUGIN_FAILURE:
                return getNextRetryDateForPluginFailure(purchasedTransactions);

            default:
                return null;
        }
    }

    private DateTime getNextRetryDateForPaymentFailure(final List<DirectPaymentTransactionModelDao> purchasedTransactions) {

        DateTime result = null;
        final List<Integer> retryDays = paymentConfig.getPaymentRetryDays();
        final int retryCount = getNumberAttemptsInState(purchasedTransactions, PaymentStatus.PAYMENT_FAILURE);
        if (retryCount < retryDays.size()) {
            int retryInDays;
            final DateTime nextRetryDate = clock.getUTCNow();
            try {
                retryInDays = retryDays.get(retryCount);
                result = nextRetryDate.plusDays(retryInDays);
            } catch (NumberFormatException ex) {
                logger.error("Could not get retry day for retry count {}", retryCount);
            }
        }
        return result;
    }

    private DateTime getNextRetryDateForPluginFailure(final List<DirectPaymentTransactionModelDao> purchasedTransactions) {

        final int retryAttempt = getNumberAttemptsInState(purchasedTransactions, PaymentStatus.PAYMENT_FAILURE);
        if (retryAttempt > paymentConfig.getPluginFailureRetryMaxAttempts()) {
            return null;
        }
        int nbSec = paymentConfig.getPluginFailureRetryStart();
        int remainingAttempts = retryAttempt;
        while (--remainingAttempts > 0) {
            nbSec = nbSec * paymentConfig.getPluginFailureRetryMultiplier();
        }
        return clock.getUTCNow().plusSeconds(nbSec);
    }

    private int getNumberAttemptsInState(final Collection<DirectPaymentTransactionModelDao> allTransactions, final PaymentStatus... statuses) {
        if (allTransactions == null || allTransactions.size() == 0) {
            return 0;
        }
        return Collections2.filter(allTransactions, new Predicate<DirectPaymentTransactionModelDao>() {
            @Override
            public boolean apply(final DirectPaymentTransactionModelDao input) {
                for (final PaymentStatus cur : statuses) {
                    if (input.getPaymentStatus() == cur) {
                        return true;
                    }
                }
                return false;
            }
        }).size();
    }

    private List<DirectPaymentTransactionModelDao> getPurchasedTransactions(final String paymentExternalKey, final InternalCallContext internalContext) {
        final DirectPaymentModelDao payment = paymentDao.getDirectPaymentByExternalKey(paymentExternalKey, internalContext);
        if (payment == null) {
            return Collections.emptyList();
        }
        final List<DirectPaymentTransactionModelDao> transactions = paymentDao.getDirectTransactionsForDirectPayment(payment.getId(), internalContext);
        if (transactions == null || transactions.size() == 0) {
            return Collections.emptyList();
        }
        return ImmutableList.copyOf(Iterables.filter(transactions, new Predicate<DirectPaymentTransactionModelDao>() {
            @Override
            public boolean apply(final DirectPaymentTransactionModelDao input) {
                return input.getTransactionType() == TransactionType.PURCHASE;
            }
        }));
    }

    private Invoice rebalanceAndGetInvoice(final UUID invoiceId, final InternalCallContext context) throws InvoiceApiException {
        final Invoice invoicePriorRebalancing = invoiceApi.getInvoiceById(invoiceId, context);
        invoiceApi.consumeExistingCBAOnAccountWithUnpaidInvoices(invoicePriorRebalancing.getAccountId(), context);
        final Invoice invoice = invoiceApi.getInvoiceById(invoiceId, context);
        return invoice;
    }

    private BigDecimal validateAndComputePaymentAmount(final Invoice invoice, @Nullable final BigDecimal inputAmount, final boolean isApiPayment) {

        if (invoice.getBalance().compareTo(BigDecimal.ZERO) <= 0) {
            logger.info("Invoice " + invoice.getId() + " has already been paid");
            return BigDecimal.ZERO;
        }
        if (isApiPayment &&
            inputAmount != null &&
            invoice.getBalance().compareTo(inputAmount) < 0) {
            logger.info("Invoice " + invoice.getId() +
                        " has a balance of " + invoice.getBalance().floatValue() +
                        " less than retry payment amount of " + inputAmount.floatValue());
            return BigDecimal.ZERO;
        }
        if (inputAmount == null) {
            return invoice.getBalance();
        } else {
            return invoice.getBalance().compareTo(inputAmount) < 0 ? invoice.getBalance() : inputAmount;
        }
    }
}
