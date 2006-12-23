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

package etm.core.plugin;

import etm.core.monitor.EtmMonitor;

/**
 * An EtmPlugin is an optional service which may be attached to
 * an existing EtmMonitor instance. An EtmPlugin should
 * offer a constructor taking an EtmMonitor argument in order
 * to get access to its managing monitor.
 *
 * @author void.fm
 * @version $Id: EtmPlugin.java,v 1.2 2006/09/23 07:03:22 french_c Exp $
 */
public interface EtmPlugin {

  /**
   * Sets the current monitor while registering it with
   * and EtmMonitor. Will always be called before {@link #start()}.
   *
   * @param aEtmMonitor The associated etmMontor instance.
   */
  public void setEtmMonitor(EtmMonitor aEtmMonitor);

  /**
   * Callback for plugin start.
   */
  public void start();

  /**
   * Callback for plugin stop()
   */

  public void stop();

}