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

package etm.core.aggregation;

import etm.core.metadata.AggregatorMetaData;
import etm.core.monitor.MeasurementPoint;

import java.util.LinkedList;

/**
 * The NestedAggregator creates a tree structure representing the collected
 * measurement results.
 *
 * @author void.fm
 * @version $Id: NestedAggregator.java,v 1.14 2006/07/16 21:49:19 french_c Exp $
 */
public class NestedAggregator extends FlatAggregator {

  public void add(MeasurementPoint point) {
    // short cut for parent == null;
    if (point.getParent() == null) {
      super.add(point);
      return;
    }

    // TODO check alternative strategy to improve performance
    // find tree root node
    LinkedList path = new LinkedList();
    path.add(point);

    MeasurementPoint rootNode = point.getParent();
    while (rootNode != null) {
      path.addFirst(rootNode);
      rootNode = rootNode.getParent();
    }

    rootNode = (MeasurementPoint) path.removeFirst();

    ExecutionAggregate aggregate = getAggregate(rootNode.getName());

    aggregate.appendPath(path);
  }

  public AggregatorMetaData getMetaData() {
    return new AggregatorMetaData(NestedAggregator.class, "An cummulating aggregator for nested representation.", false);
  }


}