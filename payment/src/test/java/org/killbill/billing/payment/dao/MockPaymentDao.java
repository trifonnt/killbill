/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

package org.killbill.billing.payment.dao;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.inject.Inject;

import org.joda.time.DateTime;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.dao.MockNonEntityDao;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.util.entity.Pagination;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class MockPaymentDao implements PaymentDao {

    private final Map<UUID, PaymentModelDao> payments = new HashMap<UUID, PaymentModelDao>();
    private final Map<UUID, PaymentTransactionModelDao> transactions = new HashMap<UUID, PaymentTransactionModelDao>();
    private final Map<UUID, PaymentAttemptModelDao> attempts = new HashMap<UUID, PaymentAttemptModelDao>();

    private final MockNonEntityDao mockNonEntityDao;

    @Inject
    public MockPaymentDao(final MockNonEntityDao mockNonEntityDao) {
        this.mockNonEntityDao = mockNonEntityDao;
    }

    public void reset() {
        synchronized (this) {
            payments.clear();
            transactions.clear();
            attempts.clear();
        }
    }

    @Override
    public int failOldPendingTransactions(final TransactionStatus newTransactionStatus, final DateTime createdBeforeDate, final InternalCallContext context) {
        int result = 0;
        synchronized (transactions) {
            for (PaymentTransactionModelDao cur : transactions.values()) {
                cur.setTransactionStatus(newTransactionStatus);
                result++;
            }
        }
        return result;
    }

    @Override
    public PaymentAttemptModelDao insertPaymentAttemptWithProperties(final PaymentAttemptModelDao attempt, final InternalCallContext context) {
        attempt.setTenantRecordId(context.getTenantRecordId());

        synchronized (this) {
            attempts.put(attempt.getId(), attempt);
            mockNonEntityDao.addTenantRecordIdMapping(attempt.getId(), context);
            return attempt;
        }
    }

    @Override
    public void updatePaymentAttempt(final UUID paymentAttemptId, final UUID transactionId, final String state, final InternalCallContext context) {
        boolean success = false;
        synchronized (this) {
            for (PaymentAttemptModelDao cur : attempts.values()) {
                if (cur.getId().equals(paymentAttemptId)) {
                    cur.setStateName(state);
                    cur.setTransactionId(transactionId);
                    success = true;
                    break;
                }
            }
        }
        if (!success) {
            throw new RuntimeException("Could not find attempt " + paymentAttemptId);
        }
    }

    @Override
    public List<PaymentAttemptModelDao> getPaymentAttemptsByState(final String stateName, final DateTime createdBeforeDate, final InternalTenantContext context) {
        return null;
    }

    @Override
    public List<PaymentAttemptModelDao> getPaymentAttempts(final String paymentExternalKey, final InternalTenantContext context) {
        synchronized (this) {
            final List<PaymentAttemptModelDao> result = new ArrayList<PaymentAttemptModelDao>();
            for (PaymentAttemptModelDao cur : attempts.values()) {
                if (cur.getPaymentExternalKey().equals(paymentExternalKey)) {
                    result.add(cur);
                }
            }
            return result;
        }
    }

    @Override
    public List<PaymentAttemptModelDao> getPaymentAttemptByTransactionExternalKey(final String transactionExternalKey, final InternalTenantContext context) {
        synchronized (this) {
            final List<PaymentAttemptModelDao> result = new ArrayList<PaymentAttemptModelDao>();
            for (PaymentAttemptModelDao cur : attempts.values()) {
                if (cur.getTransactionExternalKey().equals(transactionExternalKey)) {
                    result.add(cur);
                }
            }
            return result;
        }
    }

    @Override
    public List<PaymentTransactionModelDao> getPaymentTransactionsByExternalKey(final String transactionExternalKey, final InternalTenantContext context) {
        final List<PaymentTransactionModelDao> result = new ArrayList<PaymentTransactionModelDao>();
        synchronized (this) {
            for (PaymentTransactionModelDao cur : transactions.values()) {
                if (cur.getTransactionExternalKey().equals(transactionExternalKey)) {
                    result.add(cur);
                }
            }
        }
        return result;
    }

    @Override
    public PaymentModelDao getPaymentByExternalKey(final String externalKey, final InternalTenantContext context) {
        synchronized (this) {
            for (PaymentModelDao cur : payments.values()) {
                if (cur.getExternalKey().equals(externalKey)) {
                    return cur;
                }
            }
        }
        return null;
    }

    @Override
    public Pagination<PaymentModelDao> getPayments(final String pluginName, final Long offset, final Long limit, final InternalTenantContext context) {
        return null;
    }

    @Override
    public Pagination<PaymentModelDao> searchPayments(final String searchKey, final Long offset, final Long limit, final InternalTenantContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PaymentModelDao insertPaymentWithFirstTransaction(final PaymentModelDao payment, final PaymentTransactionModelDao paymentTransaction, final InternalCallContext context) {
        payment.setTenantRecordId(context.getTenantRecordId());
        paymentTransaction.setTenantRecordId(context.getTenantRecordId());

        synchronized (this) {
            payments.put(payment.getId(), payment);
            mockNonEntityDao.addTenantRecordIdMapping(payment.getId(), context);

            transactions.put(paymentTransaction.getId(), paymentTransaction);
            mockNonEntityDao.addTenantRecordIdMapping(paymentTransaction.getId(), context);
        }
        return payment;
    }

    @Override
    public PaymentTransactionModelDao updatePaymentWithNewTransaction(final UUID paymentId, final PaymentTransactionModelDao paymentTransaction, final InternalCallContext context) {
        paymentTransaction.setTenantRecordId(context.getTenantRecordId());

        synchronized (this) {
            transactions.put(paymentTransaction.getId(), paymentTransaction);
            mockNonEntityDao.addTenantRecordIdMapping(paymentId, context);
        }
        return paymentTransaction;
    }

    @Override
    public void updatePaymentAndTransactionOnCompletion(final UUID accountId, final UUID paymentId, final TransactionType transactionType,
                                                        final String currentPaymentStateName, final String lastSuccessPaymentStateName, final UUID transactionId,
                                                        final TransactionStatus paymentStatus, final BigDecimal processedAmount, final Currency processedCurrency,
                                                        final String gatewayErrorCode, final String gatewayErrorMsg, final InternalCallContext context) {
        synchronized (this) {
            final PaymentModelDao payment = payments.get(paymentId);
            if (payment != null) {
                payment.setStateName(currentPaymentStateName);
            }
            final PaymentTransactionModelDao transaction = transactions.get(transactionId);
            if (transaction != null) {
                transaction.setTransactionStatus(paymentStatus);
                transaction.setProcessedAmount(processedAmount);
                transaction.setProcessedCurrency(processedCurrency);
                transaction.setGatewayErrorCode(gatewayErrorCode);
                transaction.setGatewayErrorMsg(gatewayErrorMsg);
            }
        }
    }

    @Override
    public PaymentModelDao getPayment(final UUID paymentId, final InternalTenantContext context) {
        synchronized (this) {
            return payments.get(paymentId);
        }
    }

    @Override
    public PaymentTransactionModelDao getPaymentTransaction(final UUID transactionId, final InternalTenantContext context) {
        synchronized (this) {
            return transactions.get(transactionId);
        }
    }

    @Override
    public List<PaymentModelDao> getPaymentsForAccount(final UUID accountId, final InternalTenantContext context) {
        synchronized (this) {
            return ImmutableList.copyOf(Iterables.filter(payments.values(), new Predicate<PaymentModelDao>() {
                @Override
                public boolean apply(final PaymentModelDao input) {
                    return input.getAccountId().equals(accountId);
                }
            }));
        }
    }

    @Override
    public List<PaymentModelDao> getPaymentsByStates(final String[] states, final DateTime createdBeforeDate, final DateTime createdAfterDate, final int limit, final InternalTenantContext context) {
        return null;
    }

    @Override
    public List<PaymentTransactionModelDao> getTransactionsForAccount(final UUID accountId, final InternalTenantContext context) {
        synchronized (this) {
            return ImmutableList.copyOf(Iterables.filter(transactions.values(), new Predicate<PaymentTransactionModelDao>() {
                @Override
                public boolean apply(final PaymentTransactionModelDao input) {
                    final PaymentModelDao payment = payments.get(input.getPaymentId());
                    if (payment != null) {
                        return payment.getAccountId().equals(accountId);
                    } else {
                        return false;
                    }
                }
            }));
        }
    }

    @Override
    public List<PaymentTransactionModelDao> getTransactionsForPayment(final UUID paymentId, final InternalTenantContext context) {
        synchronized (this) {
            return ImmutableList.copyOf(Iterables.filter(transactions.values(), new Predicate<PaymentTransactionModelDao>() {
                @Override
                public boolean apply(final PaymentTransactionModelDao input) {
                    return input.getPaymentId().equals(paymentId);
                }
            }));
        }
    }

    @Override
    public PaymentAttemptModelDao getPaymentAttempt(final UUID attemptId, final InternalTenantContext context) {
        synchronized (this) {
            return Iterables.tryFind(attempts.values(), new Predicate<PaymentAttemptModelDao>() {
                @Override
                public boolean apply(final PaymentAttemptModelDao input) {
                    return input.getId().equals(attemptId);
                }
            }).orNull();
        }
    }

    private final List<PaymentMethodModelDao> paymentMethods = new LinkedList<PaymentMethodModelDao>();

    @Override
    public PaymentMethodModelDao insertPaymentMethod(final PaymentMethodModelDao paymentMethod, final InternalCallContext context) {
        synchronized (this) {
            paymentMethods.add(paymentMethod);
            return paymentMethod;
        }
    }

    @Override
    public PaymentMethodModelDao getPaymentMethod(final UUID paymentMethodId, final InternalTenantContext context) {
        synchronized (this) {
            for (final PaymentMethodModelDao cur : paymentMethods) {
                if (cur.getId().equals(paymentMethodId)) {
                    return cur;
                }
            }
            return null;
        }
    }

    @Override
    public PaymentMethodModelDao getPaymentMethodByExternalKey(final String paymentMethodExternalKey, final InternalTenantContext context) {
        synchronized (this) {
            for (final PaymentMethodModelDao cur : paymentMethods) {
                if (cur.getExternalKey().equals(paymentMethodExternalKey)) {
                    return cur;
                }
            }
            return null;
        }
    }

    @Override
    public List<PaymentMethodModelDao> getPaymentMethods(final UUID accountId, final InternalTenantContext context) {
        synchronized (this) {
            final List<PaymentMethodModelDao> result = new ArrayList<PaymentMethodModelDao>();
            for (final PaymentMethodModelDao cur : paymentMethods) {
                if (cur.getAccountId().equals(accountId)) {
                    result.add(cur);
                }
            }
            return result;
        }
    }

    @Override
    public Pagination<PaymentMethodModelDao> getPaymentMethods(final String pluginName, final Long offset, final Long limit, final InternalTenantContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Pagination<PaymentMethodModelDao> searchPaymentMethods(final String searchKey, final Long offset, final Long limit, final InternalTenantContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deletedPaymentMethod(final UUID paymentMethodId, final InternalCallContext context) {
        synchronized (this) {
            final Iterator<PaymentMethodModelDao> it = paymentMethods.iterator();
            while (it.hasNext()) {
                final PaymentMethodModelDao cur = it.next();
                if (cur.getId().equals(paymentMethodId)) {
                    it.remove();
                    break;
                }
            }
        }
    }

    @Override
    public List<PaymentMethodModelDao> refreshPaymentMethods(final UUID accountId, final String pluginName, final List<PaymentMethodModelDao> paymentMethods, final InternalCallContext context) {
        return ImmutableList.<PaymentMethodModelDao>of();
    }

    @Override
    public PaymentMethodModelDao getPaymentMethodIncludedDeleted(final UUID paymentMethodId, final InternalTenantContext context) {
        return getPaymentMethod(paymentMethodId, context);
    }

    @Override
    public PaymentMethodModelDao getPaymentMethodByExternalKeyIncludedDeleted(final String paymentMethodExternalKey, final InternalTenantContext context) {
        return getPaymentMethodByExternalKey(paymentMethodExternalKey, context);
    }
}
