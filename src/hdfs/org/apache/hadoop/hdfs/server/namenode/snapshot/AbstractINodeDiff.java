/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hdfs.server.namenode.snapshot;

import org.apache.hadoop.hdfs.server.namenode.INode;
import org.apache.hadoop.hdfs.server.namenode.INode.BlocksMapUpdateInfo;

/**
 * The difference of an inode between in two snapshots.
 * {@link AbstractINodeDiff2} maintains a list of snapshot diffs,
 * <pre>
 *   d_1 -> d_2 -> ... -> d_n -> null,
 * </pre>
 * where -> denotes the {@link AbstractINodeDiff#posteriorDiff} reference. The
 * current directory state is stored in the field of {@link INode}.
 * The snapshot state can be obtained by applying the diffs one-by-one in
 * reversed chronological order.  Let s_1, s_2, ..., s_n be the corresponding
 * snapshots.  Then,
 * <pre>
 *   s_n                     = (current state) - d_n;
 *   s_{n-1} = s_n - d_{n-1} = (current state) - d_n - d_{n-1};
 *   ...
 *   s_k     = s_{k+1} - d_k = (current state) - d_n - d_{n-1} - ... - d_k.
 * </pre>
 */
abstract class AbstractINodeDiff<N extends INode,
                                 D extends AbstractINodeDiff<N, D>>
    implements Comparable<Snapshot> {
  /** The snapshot will be obtained after this diff is applied. */
  final Snapshot snapshot;
  /** The snapshot inode data.  It is null when there is no change. */
  N snapshotINode;
  /**
   * Posterior diff is the diff happened after this diff.
   * The posterior diff should be first applied to obtain the posterior
   * snapshot and then apply this diff in order to obtain this snapshot.
   * If the posterior diff is null, the posterior state is the current state. 
   */
  private D posteriorDiff;

  AbstractINodeDiff(Snapshot snapshot, N snapshotINode, D posteriorDiff) {
    if (snapshot == null) {
      throw new NullPointerException("snapshot is null");
    }

    this.snapshot = snapshot;
    this.snapshotINode = snapshotINode;
    this.posteriorDiff = posteriorDiff;
  }

  /** Compare diffs with snapshot ID. */
  @Override
  public final int compareTo(final Snapshot that) {
    return Snapshot.ID_COMPARATOR.compare(this.snapshot, that);
  }

  /** @return the snapshot object of this diff. */
  final Snapshot getSnapshot() {
    return snapshot;
  }

  /** @return the posterior diff. */
  final D getPosterior() {
    return posteriorDiff;
  }

  /** @return the posterior diff. */
  final void setPosterior(D posterior) {
    posteriorDiff = posterior;
  }

  /** Copy the INode state to the snapshot if it is not done already. */
  void checkAndInitINode(N snapshotCopy) {
    if (snapshotINode == null) {
      if (snapshotCopy == null) {
        @SuppressWarnings("unchecked")
        final N right = (N)getCurrentINode().createSnapshotCopy().right;
        snapshotCopy = right;
      }
      snapshotINode = snapshotCopy;
    }
  }

  /** @return the current inode. */
  abstract N getCurrentINode();

  /** @return the inode corresponding to the snapshot. */
  N getSnapshotINode() {
    // get from this diff, then the posterior diff and then the current inode
    for(AbstractINodeDiff<N, D> d = this; ; d = d.posteriorDiff) {
      if (d.snapshotINode != null) {
        return d.snapshotINode;
      } else if (d.posteriorDiff == null) {
        return getCurrentINode();
      }
    }
  }

  /** Combine the posterior diff and collect blocks for deletion. */
  abstract void combinePosteriorAndCollectBlocks(final D posterior,
      final BlocksMapUpdateInfo collectedBlocks);
}
