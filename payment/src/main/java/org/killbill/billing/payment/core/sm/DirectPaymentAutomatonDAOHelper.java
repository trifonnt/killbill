/*
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PaymentStatus;
import org.killbill.billing.payment.dao.DirectPaymentModelDao;
import org.killbill.billing.payment.dao.DirectPaymentTransactionModelDao;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.dao.PaymentMethodModelDao;
import org.killbill.billing.payment.plugin.api.PaymentInfoPlugin;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;

public class DirectPaymentAutomatonDAOHelper {

    protected final DirectPaymentStateContext directPaymentStateContext;
    protected final DateTime utcNow;
    protected final InternalCallContext internalCallContext;

    protected final PaymentDao paymentDao;

    private final OSGIServiceRegistration<PaymentPluginApi> pluginRegistry;

    // Used to build new payments and transactions
    public DirectPaymentAutomatonDAOHelper(final DirectPaymentStateContext directPaymentStateContext,
                                           final DateTime utcNow, final PaymentDao paymentDao,
                                           final OSGIServiceRegistration<PaymentPluginApi> pluginRegistry,
                                           final InternalCallContext internalCallContext) throws PaymentApiException {
        this.directPaymentStateContext = directPaymentStateContext;
        this.utcNow = utcNow;
        this.paymentDao = paymentDao;
        this.pluginRegistry = pluginRegistry;
        this.internalCallContext = internalCallContext;
    }

    public DirectPaymentTransactionModelDao createNewDirectPaymentTransaction() {
        final DirectPaymentTransactionModelDao paymentTransactionModelDao;
        if (directPaymentStateContext.getDirectPaymentId() == null) {
            final DirectPaymentModelDao newPaymentModelDao = buildNewDirectPaymentModelDao();
            final DirectPaymentTransactionModelDao newPaymentTransactionModelDao = buildNewDirectPaymentTransactionModelDao(newPaymentModelDao.getId());

            final DirectPaymentModelDao paymentModelDao = paymentDao.insertDirectPaymentWithFirstTransaction(newPaymentModelDao, newPaymentTransactionModelDao, internalCallContext);
            paymentTransactionModelDao = paymentDao.getDirectTransactionsForAccount(directPaymentStateContext.getAccount().getId(), internalCallContext).get(0);
        } else {
            final DirectPaymentTransactionModelDao newPaymentTransactionModelDao = buildNewDirectPaymentTransactionModelDao();
            paymentTransactionModelDao = paymentDao.updateDirectPaymentWithNewTransaction(directPaymentStateContext.getDirectPaymentId(), newPaymentTransactionModelDao, internalCallContext);
        }

        return paymentTransactionModelDao;
    }

    public void processPaymentInfoPlugin(final PaymentStatus paymentStatus, @Nullable final PaymentInfoPlugin paymentInfoPlugin,
                                         final UUID directPaymentTransactionId, final String currentPaymentStateName) {
        paymentDao.updateDirectPaymentAndTransactionOnCompletion(directPaymentStateContext.getDirectPaymentId(),
                                                                 paymentStatus,
                                                                 directPaymentStateContext.getAmount(),
                                                                 directPaymentStateContext.getCurrency(),
                                                                 directPaymentTransactionId,
                                                                 paymentInfoPlugin == null ? null : paymentInfoPlugin.getGatewayErrorCode(),
                                                                 paymentInfoPlugin == null ? null : paymentInfoPlugin.getGatewayError(),
                                                                 currentPaymentStateName,
                                                                 internalCallContext);
    }

    public DirectPaymentModelDao buildNewDirectPaymentModelDao() {
        final DateTime createdDate = utcNow;
        final DateTime updatedDate = utcNow;

        return new DirectPaymentModelDao(createdDate,
                                         updatedDate,
                                         directPaymentStateContext.getAccount().getId(),
                                         directPaymentStateContext.getPaymentMethodId(),
                                         directPaymentStateContext.getDirectPaymentExternalKey());
    }

    public DirectPaymentTransactionModelDao buildNewDirectPaymentTransactionModelDao() {
        final UUID directPaymentId = directPaymentStateContext.getDirectPaymentId();
        return buildNewDirectPaymentTransactionModelDao(directPaymentId);
    }

    public DirectPaymentTransactionModelDao buildNewDirectPaymentTransactionModelDao(final UUID directPaymentId) {
        final DateTime createdDate = utcNow;
        final DateTime updatedDate = utcNow;
        final DateTime effectiveDate = utcNow;
        final String gatewayErrorCode = null;
        final String gatewayErrorMsg = null;

        return new DirectPaymentTransactionModelDao(createdDate,
                                                    updatedDate,
                                                    directPaymentStateContext.getDirectPaymentTransactionExternalKey(),
                                                    directPaymentId,
                                                    directPaymentStateContext.getTransactionType(),
                                                    effectiveDate,
                                                    PaymentStatus.UNKNOWN,
                                                    directPaymentStateContext.getAmount(),
                                                    directPaymentStateContext.getCurrency(),
                                                    gatewayErrorCode,
                                                    gatewayErrorMsg);
    }

    public UUID getDefaultPaymentMethodId(final Account account) throws PaymentApiException {
        final UUID paymentMethodId = account.getPaymentMethodId();
        if (paymentMethodId == null) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_DEFAULT_PAYMENT_METHOD, account.getId());
        }
        return paymentMethodId;
    }

    public PaymentPluginApi getPaymentProviderPlugin(final InternalTenantContext context) throws PaymentApiException {
        final UUID paymentMethodId = directPaymentStateContext.getPaymentMethodId();
        final PaymentMethodModelDao methodDao = paymentDao.getPaymentMethodIncludedDeleted(paymentMethodId, context);
        if (methodDao == null) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_PAYMENT_METHOD, paymentMethodId);
        }
        return getPaymentPluginApi(methodDao.getPluginName());
    }

    private PaymentPluginApi getPaymentPluginApi(final String pluginName) throws PaymentApiException {
        final PaymentPluginApi pluginApi = pluginRegistry.getServiceForName(pluginName);
        if (pluginApi == null) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_PAYMENT_PLUGIN, pluginName);
        }
        return pluginApi;
    }

    public DirectPaymentModelDao getDirectPayment() throws PaymentApiException {
        final DirectPaymentModelDao paymentModelDao;
        paymentModelDao = paymentDao.getDirectPayment(directPaymentStateContext.getDirectPaymentId(), internalCallContext);
        if (paymentModelDao == null) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_PAYMENT, directPaymentStateContext.getDirectPaymentId());
        }
        return paymentModelDao;
    }
}
