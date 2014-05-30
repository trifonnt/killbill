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

package org.killbill.billing.payment.dao;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.payment.api.PaymentStatus;
import org.killbill.billing.payment.api.RefundStatus;
import org.killbill.billing.util.entity.Pagination;

import com.google.common.collect.ImmutableList;

public class MockPaymentDao implements PaymentDao {

    private final Map<UUID, DirectPaymentModelDao> payments = new HashMap<UUID, DirectPaymentModelDao>();
    private final Map<UUID, DirectPaymentTransactionModelDao> transactions = new HashMap<UUID, DirectPaymentTransactionModelDao>();
    private final Map<String, PaymentAttemptModelDao> attempts = new HashMap<String, PaymentAttemptModelDao>();

    public void reset() {
        payments.clear();
        transactions.clear();
        attempts.clear();
    }
    @Override
    public PaymentAttemptModelDao insertPaymentAttempt(final PaymentAttemptModelDao attempt, final InternalCallContext context) {
        synchronized (this) {
            attempts.put(attempt.getTransactionExternalKey(), attempt);
            return attempt;
        }
    }

    @Override
    public void updatePaymentAttempt(final UUID paymentAttemptId, final String state, final InternalCallContext context) {
        boolean success = false;
        synchronized (this) {
            for (PaymentAttemptModelDao cur : attempts.values()) {
                if (cur.getId().equals(paymentAttemptId)) {
                    cur.setStateName(state);
                    success = true;
                }
            }
        }
        if (!success) {
            throw new RuntimeException("Counld not find attempt " + paymentAttemptId);
        }
    }

    @Override
    public PaymentAttemptModelDao getPaymentAttemptByExternalKey(final String externalKey, final InternalTenantContext context) {
        synchronized (this) {
            return attempts.get(externalKey);
        }
    }

    @Override
    public DirectPaymentTransactionModelDao getDirectPaymentTransactionByExternalKey(final String transactionExternalKey, final InternalTenantContext context) {
        synchronized (this) {
            for (DirectPaymentTransactionModelDao cur : transactions.values()) {
                if (cur.getTransactionExternalKey().equals(transactionExternalKey)) {
                    return cur;
                }
            }
        }
        return null;
    }

    @Override
    public Pagination<DirectPaymentModelDao> getDirectPayments(final String pluginName, final Long offset, final Long limit, final InternalTenantContext context) {
        return null;
    }

    @Override
    public DirectPaymentModelDao insertDirectPaymentWithFirstTransaction(final DirectPaymentModelDao directPayment, final DirectPaymentTransactionModelDao directPaymentTransaction, final InternalCallContext context) {
        synchronized (this) {
            payments.put(directPayment.getId(), directPayment);
            transactions.put(directPaymentTransaction.getId(), directPaymentTransaction);
        }
        return directPayment;
    }

    @Override
    public DirectPaymentTransactionModelDao updateDirectPaymentWithNewTransaction(final UUID dirctPaymentId, final DirectPaymentTransactionModelDao directPaymentTransaction, final InternalCallContext context) {
        return null;
    }

    @Override
    public void updateDirectPaymentAndTransactionOnCompletion(final UUID directPaymentId, final String currentPaymentStateName, final UUID directTransactionId, final PaymentStatus paymentStatus, final BigDecimal processedAmount, final Currency processedCurrency, final String gatewayErrorCode, final String gatewayErrorMsg, final InternalCallContext context) {
    }

    @Override
    public DirectPaymentModelDao getDirectPayment(final UUID directPaymentId, final InternalTenantContext context) {
        synchronized (this) {
            return payments.get(directPaymentId);
        }
    }

    @Override
    public DirectPaymentTransactionModelDao getDirectPaymentTransaction(final UUID directTransactionId, final InternalTenantContext context) {
        return null;
    }

    @Override
    public List<DirectPaymentModelDao> getDirectPaymentsForAccount(final UUID accountId, final InternalTenantContext context) {
        return null;
    }

    @Override
    public List<DirectPaymentTransactionModelDao> getDirectTransactionsForAccount(final UUID accountId, final InternalTenantContext context) {
        return null;
    }

    @Override
    public List<DirectPaymentTransactionModelDao> getDirectTransactionsForDirectPayment(final UUID directPaymentId, final InternalTenantContext context) {
        return null;
    }

    @Override
    public PaymentModelDao insertPaymentWithFirstAttempt(final PaymentModelDao paymentInfo, final PaymentAttemptModelDao attempt,
                                                         final InternalCallContext context) {
        return null;
    }

    @Override
    public PaymentAttemptModelDao updatePaymentWithNewAttempt(final UUID paymentId, final PaymentAttemptModelDao attempt, final InternalCallContext context) {
        return null;
    }

    @Override
    public void updatePaymentAndAttemptOnCompletion(final UUID paymentId, final PaymentStatus paymentStatus,
                                                    BigDecimal processedAmount, Currency processedCurrency,
                                                    final UUID attemptId, final String gatewayErrorCode,
                                                    final String gatewayErrorMsg,
                                                    final InternalCallContext context) {
    }

    @Override
    public PaymentAttemptModelDao getPaymentAttempt(final UUID attemptId, final InternalTenantContext context) {
        return attempts.get(attemptId);
    }

    @Override
    public List<PaymentModelDao> getPaymentsForInvoice(final UUID invoiceId, final InternalTenantContext context) {
        return null;
    }

    @Override
    public List<PaymentModelDao> getPaymentsForAccount(final UUID accountId, final InternalTenantContext context) {
        return null;
    }

    @Override
    public Pagination<PaymentModelDao> getPayments(final String pluginName, final Long offset, final Long limit, final InternalTenantContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PaymentModelDao getPayment(final UUID paymentId, final InternalTenantContext context) {
        return null;
    }

    @Override
    public List<PaymentAttemptModelDao> getAttemptsForPayment(final UUID paymentId, final InternalTenantContext context) {
        return null;
    }

    private final List<PaymentMethodModelDao> paymentMethods = new LinkedList<PaymentMethodModelDao>();

    @Override
    public PaymentMethodModelDao insertPaymentMethod(final PaymentMethodModelDao paymentMethod, final InternalCallContext context) {
        paymentMethods.add(paymentMethod);
        return paymentMethod;
    }

    @Override
    public PaymentMethodModelDao getPaymentMethod(final UUID paymentMethodId, final InternalTenantContext context) {
        for (final PaymentMethodModelDao cur : paymentMethods) {
            if (cur.getId().equals(paymentMethodId)) {
                return cur;
            }
        }
        return null;
    }

    @Override
    public List<PaymentMethodModelDao> getPaymentMethods(final UUID accountId, final InternalTenantContext context) {
        final List<PaymentMethodModelDao> result = new ArrayList<PaymentMethodModelDao>();
        for (final PaymentMethodModelDao cur : paymentMethods) {
            if (cur.getAccountId().equals(accountId)) {
                result.add(cur);
            }
        }
        return result;
    }

    @Override
    public Pagination<PaymentMethodModelDao> getPaymentMethods(final String pluginName, final Long offset, final Long limit, final InternalTenantContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deletedPaymentMethod(final UUID paymentMethodId, final InternalCallContext context) {
        final Iterator<PaymentMethodModelDao> it = paymentMethods.iterator();
        while (it.hasNext()) {
            final PaymentMethodModelDao cur = it.next();
            if (cur.getId().equals(paymentMethodId)) {
                it.remove();
                break;
            }
        }
    }

    @Override
    public List<PaymentMethodModelDao> refreshPaymentMethods(final UUID accountId, final String pluginName, final List<PaymentMethodModelDao> paymentMethods, final InternalCallContext context) {
        return ImmutableList.<PaymentMethodModelDao>of();
    }

    @Override
    public void undeletedPaymentMethod(final UUID paymentMethodId, final InternalCallContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RefundModelDao insertRefund(final RefundModelDao refundInfo, final InternalCallContext context) {
        return null;
    }

    @Override
    public void updateRefundStatus(final UUID refundId, final RefundStatus status, final BigDecimal processedAmount, final Currency processedCurrency, final InternalCallContext context) {
        return;
    }

    @Override
    public Pagination<RefundModelDao> getRefunds(final String pluginName, final Long offset, final Long limit, final InternalTenantContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RefundModelDao getRefund(final UUID refundId, final InternalTenantContext context) {
        return null;
    }

    @Override
    public List<RefundModelDao> getRefundsForPayment(final UUID paymentId, final InternalTenantContext context) {
        return Collections.emptyList();
    }

    @Override
    public List<RefundModelDao> getRefundsForAccount(final UUID accountId, final InternalTenantContext context) {
        return Collections.emptyList();
    }

    @Override
    public PaymentModelDao getLastPaymentForPaymentMethod(final UUID accountId, final UUID paymentMethodId, final InternalTenantContext context) {
        return null;
    }

    @Override
    public PaymentMethodModelDao getPaymentMethodIncludedDeleted(final UUID paymentMethodId, final InternalTenantContext context) {
        return getPaymentMethod(paymentMethodId, context);
    }
}
