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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;
import org.testng.annotations.Test;

import org.killbill.billing.payment.PaymentTestSuiteWithEmbeddedDB;

import com.google.common.collect.ImmutableList;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.fail;

public class TestPaymentDao extends PaymentTestSuiteWithEmbeddedDB {

    @Test(groups = "slow")
    public void testPaymentAttempt() {
        final UUID directTransactionId = UUID.randomUUID();
        final String transactionExternalKey = "tduteuqweq";
        final String stateName = "INIT";
        final String operationName = "AUTHORIZE";
        final String pluginName = "superPlugin";

        final UUID accountId = UUID.randomUUID();
        final PluginPropertyModelDao prop1 = new PluginPropertyModelDao("foo", transactionExternalKey, accountId, "PLUGIN", "key1", "value1", "yo", clock.getUTCNow());
        final PluginPropertyModelDao prop2 = new PluginPropertyModelDao("foo2", transactionExternalKey, accountId, "PLUGIN", "key2", "value2", "yo", clock.getUTCNow());
        final PluginPropertyModelDao prop3 = new PluginPropertyModelDao("foo3", "other", UUID.randomUUID(), "PLUGIN", "key2", "value2", "yo", clock.getUTCNow());
        final List<PluginPropertyModelDao> props = new ArrayList<PluginPropertyModelDao>();
        props.add(prop1);
        props.add(prop2);
        props.add(prop3);

        final PaymentAttemptModelDao attempt = new PaymentAttemptModelDao(clock.getUTCNow(), clock.getUTCNow(), directTransactionId, transactionExternalKey, stateName, operationName, pluginName);
        PaymentAttemptModelDao savedAttempt = paymentDao.insertPaymentAttemptWithProperties(attempt, props, internalCallContext);
        assertEquals(savedAttempt.getTransactionExternalKey(), transactionExternalKey);
        assertEquals(savedAttempt.getOperationName(), operationName);
        assertEquals(savedAttempt.getStateName(), stateName);
        assertEquals(savedAttempt.getPluginName(), pluginName);

        final List<PluginPropertyModelDao> retrievedProperties = paymentDao.getProperties(transactionExternalKey, internalCallContext);
        assertEquals(retrievedProperties.size(), 2);
        assertEquals(retrievedProperties.get(0).getAccountId(), accountId);
        assertEquals(retrievedProperties.get(0).getTransactionExternalKey(), transactionExternalKey);
        assertEquals(retrievedProperties.get(0).getPluginName(), "PLUGIN");
        assertEquals(retrievedProperties.get(0).getPaymentExternalKey(), "foo");
        assertEquals(retrievedProperties.get(0).getPropKey(), "key1");
        assertEquals(retrievedProperties.get(0).getPropValue(), "value1");
        assertEquals(retrievedProperties.get(0).getCreatedBy(), "yo");

        assertEquals(retrievedProperties.get(1).getAccountId(), accountId);
        assertEquals(retrievedProperties.get(1).getTransactionExternalKey(), transactionExternalKey);
        assertEquals(retrievedProperties.get(1).getPluginName(), "PLUGIN");
        assertEquals(retrievedProperties.get(1).getPaymentExternalKey(), "foo2");
        assertEquals(retrievedProperties.get(1).getPropKey(), "key2");
        assertEquals(retrievedProperties.get(1).getPropValue(), "value2");
        assertEquals(retrievedProperties.get(1).getCreatedBy(), "yo");

        final PaymentAttemptModelDao retrievedAttempt1 = paymentDao.getPaymentAttempt(attempt.getId(), internalCallContext);
        assertEquals(retrievedAttempt1.getTransactionExternalKey(), transactionExternalKey);
        assertEquals(retrievedAttempt1.getOperationName(), operationName);
        assertEquals(retrievedAttempt1.getStateName(), stateName);
        assertEquals(retrievedAttempt1.getPluginName(), pluginName);


        final PaymentAttemptModelDao retrievedAttempt2 = paymentDao.getPaymentAttemptByExternalKey(transactionExternalKey, internalCallContext);
        assertEquals(retrievedAttempt2.getTransactionExternalKey(), transactionExternalKey);
        assertEquals(retrievedAttempt2.getOperationName(), operationName);
        assertEquals(retrievedAttempt2.getStateName(), stateName);
        assertEquals(retrievedAttempt2.getPluginName(), pluginName);
    }


    @Test(groups = "slow")
    public void testPaymentMethod() {

        final UUID paymentMethodId = UUID.randomUUID();
        final UUID accountId = UUID.randomUUID();
        final String pluginName = "nobody";
        final Boolean isActive = Boolean.TRUE;
        final String externalPaymentId = UUID.randomUUID().toString();

        final PaymentMethodModelDao method = new PaymentMethodModelDao(paymentMethodId, null, null,
                                                                       accountId, pluginName, isActive);

        PaymentMethodModelDao savedMethod = paymentDao.insertPaymentMethod(method, internalCallContext);
        assertEquals(savedMethod.getId(), paymentMethodId);
        assertEquals(savedMethod.getAccountId(), accountId);
        assertEquals(savedMethod.getPluginName(), pluginName);
        assertEquals(savedMethod.isActive(), isActive);

        final List<PaymentMethodModelDao> result = paymentDao.getPaymentMethods(accountId, internalCallContext);
        assertEquals(result.size(), 1);
        savedMethod = result.get(0);
        assertEquals(savedMethod.getId(), paymentMethodId);
        assertEquals(savedMethod.getAccountId(), accountId);
        assertEquals(savedMethod.getPluginName(), pluginName);
        assertEquals(savedMethod.isActive(), isActive);

        paymentDao.deletedPaymentMethod(paymentMethodId, internalCallContext);

        PaymentMethodModelDao deletedPaymentMethod = paymentDao.getPaymentMethod(paymentMethodId, internalCallContext);
        assertNull(deletedPaymentMethod);

        deletedPaymentMethod = paymentDao.getPaymentMethodIncludedDeleted(paymentMethodId, internalCallContext);
        assertNotNull(deletedPaymentMethod);
        assertFalse(deletedPaymentMethod.isActive());
        assertEquals(deletedPaymentMethod.getAccountId(), accountId);
        assertEquals(deletedPaymentMethod.getId(), paymentMethodId);
        assertEquals(deletedPaymentMethod.getPluginName(), pluginName);
    }
}
