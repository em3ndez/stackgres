/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operatorframework.resource;

import java.util.function.BiConsumer;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.Watcher.Action;
import io.fabric8.kubernetes.client.WatcherException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractResourceWatcherFactory {

  private final Logger log = LoggerFactory.getLogger(getClass());

  public <T extends HasMetadata> Watcher<T> createWatcher(BiConsumer<Action, T> actionConsumer) {
    return new WatcherInstance<>(actionConsumer, new EmptyWatcherListener<>());
  }

  public <T extends HasMetadata> Watcher<T> createWatcher(BiConsumer<Action, T> actionConsumer,
      WatcherListener<T> watcherListener) {
    return new WatcherInstance<>(actionConsumer, watcherListener);
  }

  protected abstract void onError(WatcherException cause);

  protected abstract void onClose();

  private class WatcherInstance<T extends HasMetadata> implements Watcher<T> {

    private final BiConsumer<Action, T> actionConsumer;
    private final WatcherListener<T> watcherListener;

    public WatcherInstance(
        BiConsumer<Action, T> actionConsumer, WatcherListener<T> watcherListener) {
      this.actionConsumer = actionConsumer;
      this.watcherListener = watcherListener;
    }

    @Override
    public void eventReceived(Action action, T resource) {
      log.debug("Action <{}> on resource: [{}] {}.{}", action, resource.getKind(),
          resource.getMetadata().getNamespace(), resource.getMetadata().getName());
      try {
        actionConsumer.accept(action, resource);
        watcherListener.eventReceived(action, resource);
      } catch (Exception ex) {
        log.error("Error while performing action: <{}>", action, ex);
      }
    }

    @Override
    public void onClose(WatcherException cause) {
      if (cause.isHttpGone()) {
        log.warn("onClose was called due to HTTP 410: {}", cause.getMessage());
      } else {
        log.error("onClose was called, ", cause);
      }
      AbstractResourceWatcherFactory.this.onError(cause);
      watcherListener.watcherError(cause);
    }

    @Override
    public void onClose() {
      log.debug("Watcher closed");
      AbstractResourceWatcherFactory.this.onClose();
    }

  }

  public static class EmptyWatcherListener<T> implements WatcherListener<T> {
    @Override
    public void eventReceived(Action action, T resource) {
      // empty watcher, ignore
    }

    @Override
    public void watcherError(WatcherException ex) {
      // empty watcher, ignore
    }

    @Override
    public void watcherClosed() {
      // empty watcher, ignore
    }
  }

}
