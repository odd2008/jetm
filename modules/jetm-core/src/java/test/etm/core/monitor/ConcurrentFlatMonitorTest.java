/*
 *
 * Copyright (c) 2004, 2005, 2006 void.fm
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this list
 * of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice, this
 * list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 *
 * Neither the name void.fm nor the names of its contributors may be
 * used to endorse or promote products derived from this software without specific
 * prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */


package test.etm.core.monitor;

import etm.core.aggregation.ExecutionAggregate;
import etm.core.aggregation.FlatAggregator;
import etm.core.monitor.EtmMonitor;
import etm.core.monitor.FlatMonitor;
import etm.core.monitor.MeasurementPoint;
import etm.core.renderer.MeasurementRenderer;
import etm.core.timer.DefaultTimer;
import junit.framework.TestCase;
import test.etm.core.TestAggregator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Concurrency tests for flat monitor.
 *
 * @author void.fm
 * @version $Id: ConcurrentFlatMonitorTest.java,v 1.8 2006/05/23 10:00:45 french_c Exp $
 */
public class ConcurrentFlatMonitorTest extends TestCase {


  protected EtmMonitor monitor;
  protected TestAggregator aggregator;

  private Object lock = new Object();
  private int running;
  private List allPoints = new ArrayList();

  /**
   * tests two points with multiple threads.
   */
  public void testThreadedTwoPoint() throws Exception {

    final int testSize = 50;
    final int iterations = 100;
    final String testPointGroup1 = "group1";
    final String testPointGroup2 = "group2";

    Runner[] runnerGroup1 = new Runner[testSize];
    Runner[] runnerGroup2 = new Runner[testSize];


    for (int i = 0; i < testSize; i++) {
      runnerGroup1[i] = new Runner(testPointGroup1, iterations);
      runnerGroup2[i] = new Runner(testPointGroup2, iterations);
    }

    for (int i = 0; i < testSize; i++) {
      runnerGroup1[i].start();
      runnerGroup2[i].start();
    }

    do {
      synchronized (lock) {
        lock.wait();
      }
    } while (running > 0);


    final ExecutionAggregate group1 = new ExecutionAggregate(testPointGroup1);
    final ExecutionAggregate group2 = new ExecutionAggregate(testPointGroup2);

    for (int i = 0; i < allPoints.size(); i++) {
      MeasurementPoint point = (MeasurementPoint) allPoints.get(i);
      if (point.getName().equals(testPointGroup1)) {
        group1.addTransaction(point);
      } else {
        group2.addTransaction(point);
      }
    }


    assertEquals(2 * testSize * iterations, aggregator.getCounter());

    monitor.render(new MeasurementRenderer() {
      public void render(Map points) {
        assertNotNull(points);
        assertTrue(points.size() == 2);

        ExecutionAggregate aggregate = (ExecutionAggregate) points.get(testPointGroup1);

        assertNotNull(aggregate);
        assertEquals(testPointGroup1, aggregate.getName());
        assertEquals(testSize * iterations, aggregate.getMeasurements());

        assertEquals(group1.getTotal(), aggregate.getTotal(), 0.0);
        assertEquals(group1.getMin(), aggregate.getMin(), 0.0);
        assertEquals(group1.getMax(), aggregate.getMax(), 0.0);

        ExecutionAggregate aggregate2 = (ExecutionAggregate) points.get(testPointGroup2);

        assertNotNull(aggregate2);
        assertEquals(testPointGroup2, aggregate2.getName());
        assertEquals(testSize * iterations, aggregate2.getMeasurements());

        assertEquals(group2.getTotal(), aggregate2.getTotal(), 0.0);
        assertEquals(group2.getMin(), aggregate2.getMin(), 0.0);
        assertEquals(group2.getMax(), aggregate2.getMax(), 0.0);
      }
    });

  }


  protected void tearDown() throws Exception {
    monitor.start();
    monitor.reset();
    monitor = null;
  }


  protected void setUp() throws Exception {
    aggregator = new TestAggregator(new FlatAggregator());
    monitor = new FlatMonitor(new DefaultTimer(), aggregator);
    monitor.start();
  }


  class Runner extends Thread {
    List list = new ArrayList();

    private String testPointName;
    private int runs;


    public Runner(String aTestPointName, int aRuns) {
      testPointName = aTestPointName;
      runs = aRuns;
    }

    public void run() {
      synchronized (allPoints) {
        running++;
      }

      try {

        while (runs > 0) {
          final MeasurementPoint point = new MeasurementPoint(monitor, testPointName);
          Thread.sleep(1);
          point.collect();

          list.add(point);
          runs--;
        }
      } catch (InterruptedException e) {

      }

      synchronized (allPoints) {
        allPoints.addAll(list);
        running--;
      }

      synchronized (lock) {
        lock.notifyAll();
      }
    }

  }

}