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

package etm.core.monitor;

import etm.core.aggregation.Aggregator;
import etm.core.configuration.EtmMonitorFactory;
import etm.core.metadata.EtmMonitorMetaData;
import etm.core.plugin.EtmPlugin;
import etm.core.renderer.MeasurementRenderer;
import etm.core.timer.ExecutionTimer;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;


/**
 * Abstract base class for the execution time measurement monitors.
 * Derived class should synchronize on the internal lock object while
 * accessing shared resources, especially the attribute <code>aggregator</code>.
 *
 * @author void.fm
 * @version $Id: EtmMonitorSupport.java,v 1.21 2006/09/10 11:40:09 french_c Exp $
 */

public abstract class EtmMonitorSupport implements EtmMonitor {

  protected final String description;
  protected final ExecutionTimer timer;
  protected final Aggregator aggregator;
  protected final Object lock = new Object();

  private Date startTime;
  private Date lastReset;

  protected List plugins;

  private boolean started = false;
  private boolean collecting = false;

  /**
   * Creates a EtmMonitorSupport instance.
   *
   * @param aTimer      The timer to use.
   * @param aAggregator The aggregator to use.
   */

  protected EtmMonitorSupport(String aDescription, ExecutionTimer aTimer, Aggregator aAggregator) {
    description = aDescription;
    if (aTimer != null) {
      timer = aTimer;
    } else {
      timer = EtmMonitorFactory.newTimer();
    }

    if (aAggregator != null) {
      aggregator = aAggregator;
    } else {
      aggregator = getDefaultAggregator();
    }
    startTime = new Date();
    lastReset = startTime;
  }

  public final void visitPreMeasurement(MeasurementPoint measurementPoint) {
    try {
      if (!collecting || !started) {
        return;
      }

      if (measurementPoint == null) {
        return;
      }

      synchronized (lock) {
        doVisitPreMeasurement(measurementPoint);
      }

      measurementPoint.setTicks(timer.getTicksPerSecond());
      measurementPoint.setStartTime(timer.getCurrentTime());
      // catch all exceptions here
      // in order to avoid negative side effects for
      // our business logic
    } catch (Exception e) {
      // for now don't use a logging framework
      System.err.println("Caught exception within measurement code: " + e.toString());
    }
  }

  public synchronized final void visitPostCollect(MeasurementPoint measurementPoint) {
    if (!collecting || !started) {
      return;
    }

    try {
      if (measurementPoint == null) {
        return;
      }

      measurementPoint.setEndTime(timer.getCurrentTime());

      synchronized (lock) {
        doVisitPostCollect(measurementPoint);
        aggregator.add(measurementPoint);
      }
      // catch all exceptions here
      // in order to avoid negative side effects for
      // our business logic
    } catch (Exception e) {
      // for now don't use a logging framework
      System.err.println("Caught exception within measurement code: " + e.toString());
    }
  }


  public final void aggregate() {
    synchronized (lock) {
      aggregator.flush();
    }
  }

  public void render(MeasurementRenderer renderer) {
    synchronized (lock) {
      aggregator.flush();
      aggregator.render(renderer);
    }
  }

  public void reset() {
    synchronized (lock) {
      aggregator.reset();
      lastReset = new Date();
    }
  }

  public void reset(String measurementPoint) {
    synchronized (lock) {
      aggregator.reset(measurementPoint);
    }
  }

  public final EtmMonitorMetaData getMetaData() {
    return new EtmMonitorMetaData(
      getClass(), description, startTime, lastReset,
      aggregator.getMetaData(), timer.getMetaData());
  }

  public void start() {
    if (started) {
      collecting = true;
      return;
    }

    aggregator.start();
    startPlugins();
    started = true;
    collecting = true;
  }

  public void stop() {
    if (!started) {
      collecting = false;
      return;
    }

    collecting = false;
    started = false;
    aggregator.stop();

    shutdownPlugins();
  }

  public boolean isStarted() {
    return started;
  }

  public void enableCollection() {
    collecting = true;
  }

  public void disableCollection() {
    collecting = false;
  }

  public boolean isCollecting() {
    return collecting;
  }

  public void addPlugin(EtmPlugin aEtmPlugin) {
    if (plugins == null) {
      plugins = new ArrayList();
    }

    aEtmPlugin.setEtmMonitor(this);
    plugins.add(aEtmPlugin);
    if (started) {
      aEtmPlugin.start();
    }
  }

  /**
   * <p/>
   * Callback method for derived classes.
   * </p>
   * <p/>
   * This method is called immediately after the measurement point was created. Note that
   * neither {@link MeasurementPoint#getTicks()} nor {@link MeasurementPoint#getStartTime()} are set
   * at that point.
   * </p>
   *
   * @param aMeasurementPoint The measurement point just created.
   */
  protected abstract void doVisitPreMeasurement(MeasurementPoint aMeasurementPoint);


  /**
   * <p/>
   * Callback method for derived classes.
   * </p>
   * <p/>
   * This method is called immediately after the measurement point was collected and marked as
   * closed. At that point the all required information are valid for that measurement point.
   * </p>
   *
   * @param aMeasurementPoint The measurement point just collected.
   */

  protected abstract void doVisitPostCollect(MeasurementPoint aMeasurementPoint);

  protected abstract Aggregator getDefaultAggregator();


  protected void shutdownPlugins() {
    if (plugins != null) {

      for (int i = 0; i < plugins.size(); i++) {
        EtmPlugin etmPlugin = (EtmPlugin) plugins.get(i);
        try {
          etmPlugin.stop();
        } catch (Exception e) {
          // todo since I don't want to use a logging framework what do we do?
        }
      }
    }
  }

  protected void startPlugins() {
    if (plugins != null) {

      for (int i = 0; i < plugins.size(); i++) {
        EtmPlugin etmPlugin = (EtmPlugin) plugins.get(i);
        try {
          etmPlugin.start();
        } catch (Exception e) {
          // todo since I don't want to use a logging framework what do we do?
          e.printStackTrace();
        }
      }
    }
  }


}