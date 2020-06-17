// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: build/bazel/remote/execution/v2/remote_execution.proto

package build.bazel.remote.execution.v2;

public interface UpdateActionResultRequestOrBuilder extends
    // @@protoc_insertion_point(interface_extends:build.bazel.remote.execution.v2.UpdateActionResultRequest)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <pre>
   * The instance of the execution system to operate against. A server may
   * support multiple instances of the execution system (with their own workers,
   * storage, caches, etc.). The server MAY require use of this field to select
   * between them in an implementation-defined fashion, otherwise it can be
   * omitted.
   * </pre>
   *
   * <code>string instance_name = 1;</code>
   * @return The instanceName.
   */
  java.lang.String getInstanceName();
  /**
   * <pre>
   * The instance of the execution system to operate against. A server may
   * support multiple instances of the execution system (with their own workers,
   * storage, caches, etc.). The server MAY require use of this field to select
   * between them in an implementation-defined fashion, otherwise it can be
   * omitted.
   * </pre>
   *
   * <code>string instance_name = 1;</code>
   * @return The bytes for instanceName.
   */
  com.google.protobuf.ByteString
      getInstanceNameBytes();

  /**
   * <pre>
   * The digest of the [Action][build.bazel.remote.execution.v2.Action]
   * whose result is being uploaded.
   * </pre>
   *
   * <code>.build.bazel.remote.execution.v2.Digest action_digest = 2;</code>
   * @return Whether the actionDigest field is set.
   */
  boolean hasActionDigest();
  /**
   * <pre>
   * The digest of the [Action][build.bazel.remote.execution.v2.Action]
   * whose result is being uploaded.
   * </pre>
   *
   * <code>.build.bazel.remote.execution.v2.Digest action_digest = 2;</code>
   * @return The actionDigest.
   */
  build.bazel.remote.execution.v2.Digest getActionDigest();
  /**
   * <pre>
   * The digest of the [Action][build.bazel.remote.execution.v2.Action]
   * whose result is being uploaded.
   * </pre>
   *
   * <code>.build.bazel.remote.execution.v2.Digest action_digest = 2;</code>
   */
  build.bazel.remote.execution.v2.DigestOrBuilder getActionDigestOrBuilder();

  /**
   * <pre>
   * The [ActionResult][build.bazel.remote.execution.v2.ActionResult]
   * to store in the cache.
   * </pre>
   *
   * <code>.build.bazel.remote.execution.v2.ActionResult action_result = 3;</code>
   * @return Whether the actionResult field is set.
   */
  boolean hasActionResult();
  /**
   * <pre>
   * The [ActionResult][build.bazel.remote.execution.v2.ActionResult]
   * to store in the cache.
   * </pre>
   *
   * <code>.build.bazel.remote.execution.v2.ActionResult action_result = 3;</code>
   * @return The actionResult.
   */
  build.bazel.remote.execution.v2.ActionResult getActionResult();
  /**
   * <pre>
   * The [ActionResult][build.bazel.remote.execution.v2.ActionResult]
   * to store in the cache.
   * </pre>
   *
   * <code>.build.bazel.remote.execution.v2.ActionResult action_result = 3;</code>
   */
  build.bazel.remote.execution.v2.ActionResultOrBuilder getActionResultOrBuilder();

  /**
   * <pre>
   * An optional policy for the results of this execution in the remote cache.
   * The server will have a default policy if this is not provided.
   * This may be applied to both the ActionResult and the associated blobs.
   * </pre>
   *
   * <code>.build.bazel.remote.execution.v2.ResultsCachePolicy results_cache_policy = 4;</code>
   * @return Whether the resultsCachePolicy field is set.
   */
  boolean hasResultsCachePolicy();
  /**
   * <pre>
   * An optional policy for the results of this execution in the remote cache.
   * The server will have a default policy if this is not provided.
   * This may be applied to both the ActionResult and the associated blobs.
   * </pre>
   *
   * <code>.build.bazel.remote.execution.v2.ResultsCachePolicy results_cache_policy = 4;</code>
   * @return The resultsCachePolicy.
   */
  build.bazel.remote.execution.v2.ResultsCachePolicy getResultsCachePolicy();
  /**
   * <pre>
   * An optional policy for the results of this execution in the remote cache.
   * The server will have a default policy if this is not provided.
   * This may be applied to both the ActionResult and the associated blobs.
   * </pre>
   *
   * <code>.build.bazel.remote.execution.v2.ResultsCachePolicy results_cache_policy = 4;</code>
   */
  build.bazel.remote.execution.v2.ResultsCachePolicyOrBuilder getResultsCachePolicyOrBuilder();
}