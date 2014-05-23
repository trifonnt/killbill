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

import org.killbill.automaton.OperationResult;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.dispatcher.PluginDispatcher;
import org.killbill.billing.payment.plugin.api.PaymentInfoPlugin;
import org.killbill.billing.payment.plugin.api.PaymentPluginApiException;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.commons.locker.GlobalLocker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthorizeOperation extends DirectPaymentOperation {

    private final Logger logger = LoggerFactory.getLogger(AuthorizeOperation.class);

    public AuthorizeOperation(final Account account, final DirectPaymentAutomatonDAOHelper daoHelper,
                              final GlobalLocker locker, final PluginDispatcher<OperationResult> paymentPluginDispatcher,
                              final Iterable<PluginProperty> properties, final DirectPaymentStateContext directPaymentStateContext,
                              final InternalCallContext internalCallContext, final CallContext callContext) throws PaymentApiException {
        super(account, daoHelper, locker, paymentPluginDispatcher, properties, directPaymentStateContext, internalCallContext, callContext);
    }

    @Override
    protected PaymentInfoPlugin doPluginOperation() throws PaymentPluginApiException {
        logger.debug("Starting AUTHORIZE for payment {} ({} {})", directPaymentStateContext.getDirectPaymentId(), directPaymentStateContext.getAmount(), directPaymentStateContext.getCurrency());
        return plugin.authorizePayment(directPaymentStateContext.getAccount().getId(),
                                       directPaymentStateContext.getDirectPaymentId(),
                                       directPaymentStateContext.getPaymentMethodId(),
                                       directPaymentStateContext.getAmount(),
                                       directPaymentStateContext.getCurrency(),
                                       properties,
                                       callContext);
    }
}
