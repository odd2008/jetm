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

package etm.contrib.console;

import etm.contrib.console.actions.CollapsedResultViewAction;
import etm.contrib.console.actions.DetailAction;
import etm.contrib.console.actions.Error400Action;
import etm.contrib.console.actions.Error404Action;
import etm.contrib.console.actions.Error500Action;
import etm.contrib.console.actions.ExpandedResultViewAction;
import etm.contrib.console.actions.FaviconAction;
import etm.contrib.console.actions.ResetMonitorAction;
import etm.contrib.console.actions.RobotsTxtAction;
import etm.contrib.console.actions.StartMonitorAction;
import etm.contrib.console.actions.StopMonitorAction;
import etm.contrib.console.actions.StyleSheetAction;
import etm.core.monitor.EtmMonitor;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

/**
 * HttpConsoleServer is a drop-in http Server that renders EtmMonitor
 * results. By default it uses 2 worker threads for processing and listens to
 * port 40000. Use <a href="http://localhost:40000">http://localhost:40000</a>
 * to access the console.
 * <p/>
 * By default this console uses a collapsed view that renders top level measurement
 * points in an overview page and allows direct access to nested results on
 * a per-point level basis.
 * <p/>
 * By setting {@link #setExpanded(boolean)} to true all measurement points
 * including all nested ones will be rendered in a single page.
 * <p/>
 * This console is not intended for high traffic usage.
 * <p/>
 *
 * @author void.fm
 * @version $Id: HttpConsoleServer.java,v 1.8 2006/09/24 21:03:35 french_c Exp $
 */

public class HttpConsoleServer {

  //@todo Cleanup e.printstacktrace
  private static final int DEFAULT_LISTEN_PORT = 40000;
  private static final int DEFAULT_WORKER_SIZE = 2;

  protected EtmMonitor etmMonitor;

  private int listenPort = DEFAULT_LISTEN_PORT;
  private int workerSize = DEFAULT_WORKER_SIZE;
  private boolean expanded = false;

  private ResourceAccessor resourceAccessor;

  private Stack workers;
  private ListenerThread listenerThread;

  // Our actions
  private Map actions = new HashMap();

  // default actions
  private ConsoleAction error400 = new Error400Action();
  private ConsoleAction error404 = new Error404Action();
  private ConsoleAction error500 = new Error500Action();


  public HttpConsoleServer(EtmMonitor aEtmMonitor) {
    etmMonitor = aEtmMonitor;
  }


  /**
   * Overrides default listen port.
   *
   * @param aListenPort The new listen port.
   */
  public void setListenPort(int aListenPort) {
    listenPort = aListenPort;
  }

  /**
   * Enables expanded result rendering. Be aware that large or deep
   * performance measurement results may be hard to read in expanded
   * view.
   *
   * @param aExpanded True to enable expanded views.
   */

  public void setExpanded(boolean aExpanded) {
    expanded = aExpanded;
  }

  /**
   * Overrides default worker size.
   *
   * @param aWorkerSize The worker size, has to be 2 or more.
   * @throws IllegalArgumentException Thrown if size is lower than two.
   */
  public void setWorkerSize(int aWorkerSize) {
    if (workerSize < 2) {
      throw new IllegalArgumentException("Worker size has to be higher than two.");
    }
    workerSize = aWorkerSize;
  }

  public void start() {
    if (etmMonitor == null) {
      throw new IllegalStateException("Missing EtmMonitor reference.");
    }

    // load resources into memory
    resourceAccessor = new ResourceAccessor();

    preloadSupportedActions();

    // create our worker pool
    workers = new Stack();
    for (int i = 0; i < workerSize; i++) {
      ConsoleWorker item = new ConsoleWorker("JETM HTTP Console Worker - " + (i + 1));
      item.setDaemon(true);
      item.start();
      workers.push(item);
    }

    listenerThread = new ListenerThread();
    listenerThread.start();
  }

  public void stop() {
    synchronized (this) {
      for (int i = 0; i < workers.size(); i++) {
        ConsoleWorker worker = (ConsoleWorker) workers.get(i);
        worker.shouldStop();
      }
      workers.clear();
    }

    listenerThread.shutdown();
  }

  protected ConsoleWorker getWorker() {
    synchronized (this) {
      if (workers.size() > 0) {
        return (ConsoleWorker) workers.pop();
      } else {
        return null;
      }
    }
  }

  protected void returnWorker(ConsoleWorker aConsoleWorker) {
    synchronized (this) {
      workers.push(aConsoleWorker);
    }
  }

  private void preloadSupportedActions() {
    if (expanded) {
      actions.put("/", new ExpandedResultViewAction());
    } else {
      actions.put("/", new CollapsedResultViewAction());
      actions.put("/detail", new DetailAction());
    }

    actions.put("/reset", new ResetMonitorAction());
    actions.put("/start", new StartMonitorAction());
    actions.put("/stop", new StopMonitorAction());

    // content requests
    actions.put("/style.css", new StyleSheetAction());
    actions.put("/robots.txt", new RobotsTxtAction());
    actions.put("/favicon.ico", new FaviconAction());
  }


  class ListenerThread extends Thread {
    private boolean shouldRun = true;
    private ServerSocket socket;

    public ListenerThread() {
      super("JETM HTTP Console Listener - Port " + listenPort);
    }

    public void run() {
      try {
        socket = new ServerSocket(listenPort);
      } catch (IOException e) {
        throw new ConsoleException(e);
      }


      while (shouldRun) {
        try {
          Socket clientSocket = socket.accept();

          ConsoleWorker worker = getWorker();
          if (worker != null) {
            worker.setClientSocket(clientSocket);
          } else {
            // process in current thread
            new ConsoleWorker().process(clientSocket);
          }
        } catch (Exception e) {
          // todo exception logging?!
          if (shouldRun) {
            e.printStackTrace();
          } else {
            // don't do anything. we are shutting down probably
            // so there is no need to log the exception
          }
        }

      }
    }

    public void shutdown() {
      shouldRun = false;
      if (socket != null) {
        try {
          socket.close();
        } catch (IOException e) {
          // ignored
        }
      }
      socket = null;
    }
  }


  /**
   * A Worker that processes incoming HTTP reqests.
   */
  class ConsoleWorker extends Thread {

    private Socket clientSocket;
    private boolean shouldRun = true;


    public ConsoleWorker() {
      super();
    }

    public ConsoleWorker(String workerName) {
      super(workerName);
    }


    public void setClientSocket(Socket aClientSocket) {
      clientSocket = aClientSocket;
      synchronized (this) {
        notifyAll();
      }
    }

    public void shouldStop() {
      shouldRun = false;
      synchronized (this) {
        notifyAll();
      }
    }

    public void run() {
      while (shouldRun) {
        try {

          synchronized (this) {
            try {
              wait();
            } catch (InterruptedException e) {
              // ignored
            }
          }
          if (shouldRun) {
            process(clientSocket);
          }
        } catch (InterruptedIOException e) {
          // ignored, just close socket
        } catch (Exception e) {
          e.printStackTrace();
        } finally {
          returnWorker(this);
        }
      }
    }


    protected void process(Socket aClientSocket) throws IOException {
      try {
        aClientSocket.setSoTimeout(15 * 1000);
        BufferedInputStream inputStream = new BufferedInputStream(aClientSocket.getInputStream());
        byte[] temp = new byte[3192];

        int i = 0;
        while (i < temp.length) {
          int r = inputStream.read(temp, i, temp.length - i);
          if (r == -1) {
            return;
          } else {
            // extract first line only and delegate
            // to process
            for (int j = i; j < i + r; j++) {
              if (temp[j] == '\r' || temp[j] == '\n') {

                int endOfLine = i + j;

                BufferedOutputStream out = new BufferedOutputStream(aClientSocket.getOutputStream());

                process(out, temp, endOfLine);

                out.flush();
                out.close();
                return;
              }

            }
            i += r;
          }
        }
      } finally {
        try {
          aClientSocket.close();
        } catch (IOException e) {
          // ignored
        }
      }
    }

    protected void process(OutputStream out, byte[] aTemp, int endOfLine) throws IOException {
      ConsoleRequest consoleRequest = new ConsoleRequest(etmMonitor, resourceAccessor);
      // if we don't find an action it is a bad request
      ConsoleAction action = error400;

      // do we have an GET request
      try {
        if (endOfLine >= 5 && (aTemp[0] == 'G') && (aTemp[1] == 'E') && (aTemp[2] == 'T')) {
          // extract request name and parameters
          int endOfRequestString = 0;
          int parameterStart = 0;

          for (int i = 4; i < endOfLine; i++) {
            if (aTemp[i] == ' ') {
              endOfRequestString = i;
              break;
            } else if (aTemp[i] == '?') {
              if (parameterStart == 0) {
                parameterStart = i;
              }
            }
          }

          if (endOfRequestString > 0) {
            String requestName;

            // do we have get parameters in our request
            if (parameterStart > 0) {
              requestName = new String(aTemp, 4, parameterStart - 4);

              Map parameters = ConsoleUtil.extractRequestParameters(aTemp, parameterStart, endOfRequestString);
              consoleRequest.setRequestParameters(parameters);
            } else {
              requestName = new String(aTemp, 4, endOfRequestString - 4);
            }

            action = (ConsoleAction) actions.get(requestName);

            if (action == null) {
              // unsupported request
              action = error404;
            }
          }
        }
      } catch (Exception e) {
        // todo ??
        e.printStackTrace();
        action = error500;
      }

      action.execute(consoleRequest, out);
    }

  }

}