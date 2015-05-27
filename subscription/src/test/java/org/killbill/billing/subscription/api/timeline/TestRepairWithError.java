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

package org.killbill.billing.subscription.api.timeline;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.subscription.SubscriptionTestSuiteNoDB;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;
import org.killbill.billing.subscription.api.timeline.SubscriptionBaseTimeline.DeletedEvent;
import org.killbill.billing.subscription.api.timeline.SubscriptionBaseTimeline.NewEvent;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionBase;
import org.killbill.billing.subscription.api.user.SubscriptionBaseApiException;
import org.killbill.billing.subscription.api.user.TestSubscriptionHelper.TestWithException;
import org.killbill.billing.subscription.api.user.TestSubscriptionHelper.TestWithExceptionCallback;

import static org.testng.Assert.assertEquals;

public class TestRepairWithError extends SubscriptionTestSuiteNoDB {

    private static final String baseProduct = "Shotgun";
    private TestWithException test;
    private SubscriptionBase baseSubscription;

    @Override
    @BeforeMethod(groups = "fast")
    public void beforeMethod() throws Exception {
        super.beforeMethod();
        test = new TestWithException();
        final DateTime startDate = clock.getUTCNow();
        baseSubscription = testUtil.createSubscription(bundle, baseProduct, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, startDate);
    }

    @Test(groups = "fast")
    public void testENT_REPAIR_NEW_EVENT_BEFORE_LAST_BP_REMAINING() throws Exception {
        test.withException(new TestWithExceptionCallback() {
            @Override
            public void doTest() throws SubscriptionBaseRepairException {
                // MOVE AFTER TRIAL
                testListener.pushExpectedEvent(NextEvent.PHASE);

                final Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(40));
                clock.addDeltaFromReality(it.toDurationMillis());

                assertListenerStatus();

                final BundleBaseTimeline bundleRepair = repairApi.getBundleTimeline(bundle.getId(), callContext);
                testUtil.sortEventsOnBundle(bundleRepair);
                final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Assault-Rifle", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN);
                final NewEvent ne = testUtil.createNewEvent(SubscriptionBaseTransitionType.CHANGE, baseSubscription.getStartDate().plusDays(10), spec);

                final SubscriptionBaseTimeline sRepair = testUtil.createSubscriptionRepair(baseSubscription.getId(), Collections.<DeletedEvent>emptyList(), Collections.singletonList(ne));

                final BundleBaseTimeline bRepair = testUtil.createBundleRepair(bundle.getId(), bundleRepair.getViewId(), Collections.singletonList(sRepair));

                repairApi.repairBundle(bRepair, true, callContext);
            }
        }, ErrorCode.SUB_REPAIR_NEW_EVENT_BEFORE_LAST_BP_REMAINING);
    }

    @Test(groups = "fast")
    public void testENT_REPAIR_INVALID_DELETE_SET() throws Exception {
        test.withException(new TestWithExceptionCallback() {
            @Override
            public void doTest() throws SubscriptionBaseRepairException, SubscriptionBaseApiException {

                Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(3));
                clock.addDeltaFromReality(it.toDurationMillis());

                testListener.pushExpectedEvent(NextEvent.CHANGE);
                final DateTime changeTime = clock.getUTCNow();
                baseSubscription.changePlanWithDate("Assault-Rifle", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null, changeTime, callContext);
                assertListenerStatus();

                // MOVE AFTER TRIAL
                testListener.pushExpectedEvent(NextEvent.PHASE);
                it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(40));
                clock.addDeltaFromReality(it.toDurationMillis());
                assertListenerStatus();

                final BundleBaseTimeline bundleRepair = repairApi.getBundleTimeline(bundle.getId(), callContext);
                testUtil.sortEventsOnBundle(bundleRepair);
                final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Assault-Rifle", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN);
                final NewEvent ne = testUtil.createNewEvent(SubscriptionBaseTransitionType.CHANGE, baseSubscription.getStartDate().plusDays(10), spec);
                final DeletedEvent de = testUtil.createDeletedEvent(bundleRepair.getSubscriptions().get(0).getExistingEvents().get(1).getEventId());

                final SubscriptionBaseTimeline sRepair = testUtil.createSubscriptionRepair(baseSubscription.getId(), Collections.singletonList(de), Collections.singletonList(ne));
                final BundleBaseTimeline bRepair = testUtil.createBundleRepair(bundle.getId(), bundleRepair.getViewId(), Collections.singletonList(sRepair));

                repairApi.repairBundle(bRepair, true, callContext);
            }
        }, ErrorCode.SUB_REPAIR_INVALID_DELETE_SET);
    }

    @Test(groups = "fast")
    public void testENT_REPAIR_NON_EXISTENT_DELETE_EVENT() throws Exception {
        test.withException(new TestWithExceptionCallback() {
            @Override
            public void doTest() throws SubscriptionBaseRepairException {

                final BundleBaseTimeline bundleRepair = repairApi.getBundleTimeline(bundle.getId(), callContext);
                testUtil.sortEventsOnBundle(bundleRepair);
                final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Assault-Rifle", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN);
                final NewEvent ne = testUtil.createNewEvent(SubscriptionBaseTransitionType.CHANGE, baseSubscription.getStartDate().plusDays(10), spec);
                final DeletedEvent de = testUtil.createDeletedEvent(UUID.randomUUID());
                final SubscriptionBaseTimeline sRepair = testUtil.createSubscriptionRepair(baseSubscription.getId(), Collections.singletonList(de), Collections.singletonList(ne));

                final BundleBaseTimeline bRepair = testUtil.createBundleRepair(bundle.getId(), bundleRepair.getViewId(), Collections.singletonList(sRepair));

                repairApi.repairBundle(bRepair, true, callContext);
            }
        }, ErrorCode.SUB_REPAIR_NON_EXISTENT_DELETE_EVENT);
    }

    @Test(groups = "fast")
    public void testENT_REPAIR_SUB_RECREATE_NOT_EMPTY() throws Exception {
        test.withException(new TestWithExceptionCallback() {
            @Override
            public void doTest() throws SubscriptionBaseRepairException {

                // MOVE AFTER TRIAL
                testListener.pushExpectedEvent(NextEvent.PHASE);
                final Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(40));
                clock.addDeltaFromReality(it.toDurationMillis());
                assertListenerStatus();

                final BundleBaseTimeline bundleRepair = repairApi.getBundleTimeline(bundle.getId(), callContext);
                testUtil.sortEventsOnBundle(bundleRepair);
                final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Assault-Rifle", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN);
                final NewEvent ne = testUtil.createNewEvent(SubscriptionBaseTransitionType.CREATE, baseSubscription.getStartDate().plusDays(10), spec);
                final List<DeletedEvent> des = new LinkedList<SubscriptionBaseTimeline.DeletedEvent>();
                des.add(testUtil.createDeletedEvent(bundleRepair.getSubscriptions().get(0).getExistingEvents().get(1).getEventId()));
                final SubscriptionBaseTimeline sRepair = testUtil.createSubscriptionRepair(baseSubscription.getId(), des, Collections.singletonList(ne));

                final BundleBaseTimeline bRepair = testUtil.createBundleRepair(bundle.getId(), bundleRepair.getViewId(), Collections.singletonList(sRepair));

                repairApi.repairBundle(bRepair, true, callContext);

            }
        }, ErrorCode.SUB_REPAIR_SUB_RECREATE_NOT_EMPTY);
    }

    @Test(groups = "fast")
    public void testENT_REPAIR_SUB_EMPTY() throws Exception {
        test.withException(new TestWithExceptionCallback() {

            @Override
            public void doTest() throws SubscriptionBaseRepairException {

                // MOVE AFTER TRIAL
                testListener.pushExpectedEvent(NextEvent.PHASE);
                final Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(40));
                clock.addDeltaFromReality(it.toDurationMillis());
                assertListenerStatus();

                final BundleBaseTimeline bundleRepair = repairApi.getBundleTimeline(bundle.getId(), callContext);
                testUtil.sortEventsOnBundle(bundleRepair);
                final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Assault-Rifle", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN);
                final NewEvent ne = testUtil.createNewEvent(SubscriptionBaseTransitionType.CHANGE, baseSubscription.getStartDate().plusDays(10), spec);
                final List<DeletedEvent> des = new LinkedList<SubscriptionBaseTimeline.DeletedEvent>();
                des.add(testUtil.createDeletedEvent(bundleRepair.getSubscriptions().get(0).getExistingEvents().get(0).getEventId()));
                des.add(testUtil.createDeletedEvent(bundleRepair.getSubscriptions().get(0).getExistingEvents().get(1).getEventId()));
                final SubscriptionBaseTimeline sRepair = testUtil.createSubscriptionRepair(baseSubscription.getId(), des, Collections.singletonList(ne));

                final BundleBaseTimeline bRepair = testUtil.createBundleRepair(bundle.getId(), bundleRepair.getViewId(), Collections.singletonList(sRepair));

                repairApi.repairBundle(bRepair, true, callContext);
            }
        }, ErrorCode.SUB_REPAIR_SUB_EMPTY);
    }

    @Test(groups = "fast")
    public void testENT_REPAIR_AO_CREATE_BEFORE_BP_START() throws Exception {
        test.withException(new TestWithExceptionCallback() {
            @Override
            public void doTest() throws SubscriptionBaseRepairException, SubscriptionBaseApiException {
                // MOVE CLOCK A LITTLE BIT-- STILL IN TRIAL
                Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(4));
                clock.addDeltaFromReality(it.toDurationMillis());
                final DefaultSubscriptionBase aoSubscription = testUtil.createSubscription(bundle, "Telescopic-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME);

                // MOVE CLOCK A LITTLE BIT MORE -- STILL IN TRIAL
                it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(4));
                clock.addDeltaFromReality(it.toDurationMillis());

                final BundleBaseTimeline bundleRepair = repairApi.getBundleTimeline(bundle.getId(), callContext);
                testUtil.sortEventsOnBundle(bundleRepair);

                // Quick check
                final SubscriptionBaseTimeline bpRepair = testUtil.getSubscriptionRepair(baseSubscription.getId(), bundleRepair);
                assertEquals(bpRepair.getExistingEvents().size(), 2);

                final SubscriptionBaseTimeline aoRepair = testUtil.getSubscriptionRepair(aoSubscription.getId(), bundleRepair);
                assertEquals(aoRepair.getExistingEvents().size(), 2);

                final List<DeletedEvent> des = new LinkedList<SubscriptionBaseTimeline.DeletedEvent>();
                des.add(testUtil.createDeletedEvent(aoRepair.getExistingEvents().get(0).getEventId()));
                des.add(testUtil.createDeletedEvent(aoRepair.getExistingEvents().get(1).getEventId()));

                final DateTime aoRecreateDate = aoSubscription.getStartDate().minusDays(5);
                final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Telescopic-Scope", ProductCategory.ADD_ON, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.DISCOUNT);
                final NewEvent ne = testUtil.createNewEvent(SubscriptionBaseTransitionType.CREATE, aoRecreateDate, spec);

                final SubscriptionBaseTimeline saoRepair = testUtil.createSubscriptionRepair(aoSubscription.getId(), des, Collections.singletonList(ne));

                final BundleBaseTimeline bRepair = testUtil.createBundleRepair(bundle.getId(), bundleRepair.getViewId(), Collections.singletonList(saoRepair));

                final boolean dryRun = true;
                repairApi.repairBundle(bRepair, dryRun, callContext);
            }
        }, ErrorCode.SUB_REPAIR_AO_CREATE_BEFORE_BP_START);
    }

    @Test(groups = "fast")
    public void testENT_REPAIR_NEW_EVENT_BEFORE_LAST_AO_REMAINING() throws Exception {
        test.withException(new TestWithExceptionCallback() {
            @Override
            public void doTest() throws SubscriptionBaseRepairException, SubscriptionBaseApiException {

                // MOVE CLOCK A LITTLE BIT-- STILL IN TRIAL
                Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(4));
                clock.addDeltaFromReality(it.toDurationMillis());
                final DefaultSubscriptionBase aoSubscription = testUtil.createSubscription(bundle, "Telescopic-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME);

                // MOVE CLOCK A LITTLE BIT MORE -- STILL IN TRIAL
                it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(4));
                clock.addDeltaFromReality(it.toDurationMillis());

                BundleBaseTimeline bundleRepair = repairApi.getBundleTimeline(bundle.getId(), callContext);
                testUtil.sortEventsOnBundle(bundleRepair);

                // Quick check
                final SubscriptionBaseTimeline bpRepair = testUtil.getSubscriptionRepair(baseSubscription.getId(), bundleRepair);
                assertEquals(bpRepair.getExistingEvents().size(), 2);

                final SubscriptionBaseTimeline aoRepair = testUtil.getSubscriptionRepair(aoSubscription.getId(), bundleRepair);
                assertEquals(aoRepair.getExistingEvents().size(), 2);

                final List<DeletedEvent> des = new LinkedList<SubscriptionBaseTimeline.DeletedEvent>();
                //des.add(createDeletedEvent(aoRepair.getExistingEvents().get(1).getEventId()));        
                final DateTime aoCancelDate = aoSubscription.getStartDate().plusDays(10);

                final NewEvent ne = testUtil.createNewEvent(SubscriptionBaseTransitionType.CANCEL, aoCancelDate, null);

                final SubscriptionBaseTimeline saoRepair = testUtil.createSubscriptionRepair(aoSubscription.getId(), des, Collections.singletonList(ne));

                bundleRepair = testUtil.createBundleRepair(bundle.getId(), bundleRepair.getViewId(), Collections.singletonList(saoRepair));

                final boolean dryRun = true;
                repairApi.repairBundle(bundleRepair, dryRun, callContext);
            }
        }, ErrorCode.SUB_REPAIR_NEW_EVENT_BEFORE_LAST_AO_REMAINING);
    }

    @Test(groups = "fast", enabled = false) // TODO - fails on jdk7 on Travis
    public void testENT_REPAIR_BP_RECREATE_MISSING_AO() throws Exception {
        test.withException(new TestWithExceptionCallback() {
            @Override
            public void doTest() throws SubscriptionBaseRepairException, SubscriptionBaseApiException {

                //testListener.pushExpectedEvent(NextEvent.PHASE);

                final Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(4));
                clock.addDeltaFromReality(it.toDurationMillis());
                //assertListenerStatus();

                final DefaultSubscriptionBase aoSubscription = testUtil.createSubscription(bundle, "Laser-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME);

                final BundleBaseTimeline bundleRepair = repairApi.getBundleTimeline(bundle.getId(), callContext);
                testUtil.sortEventsOnBundle(bundleRepair);

                final DateTime newCreateTime = baseSubscription.getStartDate().plusDays(3);

                final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Pistol", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.TRIAL);

                final NewEvent ne = testUtil.createNewEvent(SubscriptionBaseTransitionType.CREATE, newCreateTime, spec);
                final List<DeletedEvent> des = new LinkedList<SubscriptionBaseTimeline.DeletedEvent>();
                des.add(testUtil.createDeletedEvent(bundleRepair.getSubscriptions().get(0).getExistingEvents().get(0).getEventId()));
                des.add(testUtil.createDeletedEvent(bundleRepair.getSubscriptions().get(0).getExistingEvents().get(1).getEventId()));

                final SubscriptionBaseTimeline sRepair = testUtil.createSubscriptionRepair(baseSubscription.getId(), des, Collections.singletonList(ne));

                // FIRST ISSUE DRY RUN
                final BundleBaseTimeline bRepair = testUtil.createBundleRepair(bundle.getId(), bundleRepair.getViewId(), Collections.singletonList(sRepair));

                final boolean dryRun = true;
                repairApi.repairBundle(bRepair, dryRun, callContext);
            }
        }, ErrorCode.SUB_REPAIR_BP_RECREATE_MISSING_AO);
    }

    //
    // CAN'T seem to trigger such case easily, other errors trigger before...
    //
    @Test(groups = "fast", enabled = false)
    public void testENT_REPAIR_BP_RECREATE_MISSING_AO_CREATE() throws Exception {
        test.withException(new TestWithExceptionCallback() {
            @Override
            public void doTest() throws SubscriptionBaseRepairException, SubscriptionBaseApiException {
                /*
              //testListener.pushExpectedEvent(NextEvent.PHASE);

                Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(4));
                clock.addDeltaFromReality(it.toDurationMillis());


                DefaultSubscriptionBase aoSubscription = createSubscription("Laser-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME);
                
                BundleRepair bundleRepair = repairApi.getBundleRepair(bundle.getId());
                sortEventsOnBundle(bundleRepair);
                
                DateTime newCreateTime = baseSubscription.getStartDate().plusDays(3);

                PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Pistol", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.TRIAL);

                NewEvent ne = createNewEvent(SubscriptionBaseTransitionType.CREATE, newCreateTime, spec);
                List<DeletedEvent> des = new LinkedList<SubscriptionRepair.DeletedEvent>();
                des.add(createDeletedEvent(bundleRepair.getSubscriptions().get(0).getExistingEvents().get(0).getEventId()));
                des.add(createDeletedEvent(bundleRepair.getSubscriptions().get(0).getExistingEvents().get(1).getEventId()));

                SubscriptionRepair bpRepair = createSubscriptionReapir(baseSubscription.getId(), des, Collections.singletonList(ne));
                
                ne = createNewEvent(SubscriptionBaseTransitionType.CANCEL, clock.getUTCNow().minusDays(1),  null);
                SubscriptionRepair aoRepair = createSubscriptionReapir(aoSubscription.getId(), Collections.<SubscriptionRepair.DeletedEvent>emptyList(), Collections.singletonList(ne));
                
                
                List<SubscriptionRepair> allRepairs = new LinkedList<SubscriptionRepair>();
                allRepairs.add(bpRepair);
                allRepairs.add(aoRepair);
                bundleRepair =  createBundleRepair(bundle.getId(), bundleRepair.getViewId(), allRepairs);
                // FIRST ISSUE DRY RUN
                BundleRepair bRepair =  createBundleRepair(bundle.getId(), bundleRepair.getViewId(), allRepairs);
                
                boolean dryRun = true;
                repairApi.repairBundle(bRepair, dryRun, callcontext);
                */
            }
        }, ErrorCode.SUB_REPAIR_BP_RECREATE_MISSING_AO_CREATE);
    }

    @Test(groups = "fast", enabled = false)
    public void testENT_REPAIR_MISSING_AO_DELETE_EVENT() throws Exception {
        test.withException(new TestWithExceptionCallback() {
            @Override
            public void doTest() throws SubscriptionBaseRepairException, SubscriptionBaseApiException {

                /*
                // MOVE CLOCK -- JUST BEFORE END OF TRIAL
                 *                 
                Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(29));
                clock.addDeltaFromReality(it.toDurationMillis());

                clock.setDeltaFromReality(getDurationDay(29), 0);
                
                DefaultSubscriptionBase aoSubscription = createSubscription("Laser-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME);
                
                // MOVE CLOCK -- RIGHT OUT OF TRIAL
                testListener.pushExpectedEvent(NextEvent.PHASE);                
                clock.addDeltaFromReality(getDurationDay(5));
                assertListenerStatus();

                DateTime requestedChange = clock.getUTCNow();
                baseSubscription.changePlanWithRequestedDate("Assault-Rifle", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, requestedChange, callcontext);

                DateTime reapairTime = clock.getUTCNow().minusDays(1);

                BundleRepair bundleRepair = repairApi.getBundleRepair(bundle.getId());
                sortEventsOnBundle(bundleRepair);
                
                SubscriptionRepair bpRepair = getSubscriptionRepair(baseSubscription.getId(), bundleRepair);
                SubscriptionRepair aoRepair = getSubscriptionRepair(aoSubscription.getId(), bundleRepair);

                List<DeletedEvent> bpdes = new LinkedList<SubscriptionRepair.DeletedEvent>();
                bpdes.add(createDeletedEvent(bpRepair.getExistingEvents().get(2).getEventId()));    
                bpRepair = createSubscriptionReapir(baseSubscription.getId(), bpdes, Collections.<NewEvent>emptyList());
                
                NewEvent ne = createNewEvent(SubscriptionBaseTransitionType.CANCEL, reapairTime, null);
                aoRepair = createSubscriptionReapir(aoSubscription.getId(), Collections.<SubscriptionRepair.DeletedEvent>emptyList(), Collections.singletonList(ne));
                
                List<SubscriptionRepair> allRepairs = new LinkedList<SubscriptionRepair>();
                allRepairs.add(bpRepair);
                allRepairs.add(aoRepair);
                bundleRepair =  createBundleRepair(bundle.getId(), bundleRepair.getViewId(), allRepairs);
                
                boolean dryRun = false;
                repairApi.repairBundle(bundleRepair, dryRun, callcontext);
                */
            }
        }, ErrorCode.SUB_REPAIR_MISSING_AO_DELETE_EVENT);
    }

}
