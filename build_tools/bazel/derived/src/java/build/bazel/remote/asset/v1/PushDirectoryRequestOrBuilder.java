// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: build/bazel/remote/asset/v1/remote_asset.proto

package build.bazel.remote.asset.v1;

public interface PushDirectoryRequestOrBuilder extends
    // @@protoc_insertion_point(interface_extends:build.bazel.remote.asset.v1.PushDirectoryRequest)
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
   * The URI(s) of the content to associate. If multiple URIs are specified, the
   * pushed content will be available to fetch by specifying any of them.
   * </pre>
   *
   * <code>repeated string uris = 2;</code>
   * @return A list containing the uris.
   */
  java.util.List<java.lang.String>
      getUrisList();
  /**
   * <pre>
   * The URI(s) of the content to associate. If multiple URIs are specified, the
   * pushed content will be available to fetch by specifying any of them.
   * </pre>
   *
   * <code>repeated string uris = 2;</code>
   * @return The count of uris.
   */
  int getUrisCount();
  /**
   * <pre>
   * The URI(s) of the content to associate. If multiple URIs are specified, the
   * pushed content will be available to fetch by specifying any of them.
   * </pre>
   *
   * <code>repeated string uris = 2;</code>
   * @param index The index of the element to return.
   * @return The uris at the given index.
   */
  java.lang.String getUris(int index);
  /**
   * <pre>
   * The URI(s) of the content to associate. If multiple URIs are specified, the
   * pushed content will be available to fetch by specifying any of them.
   * </pre>
   *
   * <code>repeated string uris = 2;</code>
   * @param index The index of the value to return.
   * @return The bytes of the uris at the given index.
   */
  com.google.protobuf.ByteString
      getUrisBytes(int index);

  /**
   * <pre>
   * Qualifiers sub-specifying the content that is being pushed - see comments
   * on [Qualifier][build.bazel.remote.asset.v1.Qualifier].
   * The same qualifiers apply to all URIs.
   * </pre>
   *
   * <code>repeated .build.bazel.remote.asset.v1.Qualifier qualifiers = 3;</code>
   */
  java.util.List<build.bazel.remote.asset.v1.Qualifier> 
      getQualifiersList();
  /**
   * <pre>
   * Qualifiers sub-specifying the content that is being pushed - see comments
   * on [Qualifier][build.bazel.remote.asset.v1.Qualifier].
   * The same qualifiers apply to all URIs.
   * </pre>
   *
   * <code>repeated .build.bazel.remote.asset.v1.Qualifier qualifiers = 3;</code>
   */
  build.bazel.remote.asset.v1.Qualifier getQualifiers(int index);
  /**
   * <pre>
   * Qualifiers sub-specifying the content that is being pushed - see comments
   * on [Qualifier][build.bazel.remote.asset.v1.Qualifier].
   * The same qualifiers apply to all URIs.
   * </pre>
   *
   * <code>repeated .build.bazel.remote.asset.v1.Qualifier qualifiers = 3;</code>
   */
  int getQualifiersCount();
  /**
   * <pre>
   * Qualifiers sub-specifying the content that is being pushed - see comments
   * on [Qualifier][build.bazel.remote.asset.v1.Qualifier].
   * The same qualifiers apply to all URIs.
   * </pre>
   *
   * <code>repeated .build.bazel.remote.asset.v1.Qualifier qualifiers = 3;</code>
   */
  java.util.List<? extends build.bazel.remote.asset.v1.QualifierOrBuilder> 
      getQualifiersOrBuilderList();
  /**
   * <pre>
   * Qualifiers sub-specifying the content that is being pushed - see comments
   * on [Qualifier][build.bazel.remote.asset.v1.Qualifier].
   * The same qualifiers apply to all URIs.
   * </pre>
   *
   * <code>repeated .build.bazel.remote.asset.v1.Qualifier qualifiers = 3;</code>
   */
  build.bazel.remote.asset.v1.QualifierOrBuilder getQualifiersOrBuilder(
      int index);

  /**
   * <pre>
   * A time after which this content should stop being returned via
   * [FetchDirectory][build.bazel.remote.asset.v1.Fetch.FetchDirectory].
   * Servers *MAY* expire content early, e.g. due to storage pressure.
   * </pre>
   *
   * <code>.google.protobuf.Timestamp expire_at = 4;</code>
   * @return Whether the expireAt field is set.
   */
  boolean hasExpireAt();
  /**
   * <pre>
   * A time after which this content should stop being returned via
   * [FetchDirectory][build.bazel.remote.asset.v1.Fetch.FetchDirectory].
   * Servers *MAY* expire content early, e.g. due to storage pressure.
   * </pre>
   *
   * <code>.google.protobuf.Timestamp expire_at = 4;</code>
   * @return The expireAt.
   */
  com.google.protobuf.Timestamp getExpireAt();
  /**
   * <pre>
   * A time after which this content should stop being returned via
   * [FetchDirectory][build.bazel.remote.asset.v1.Fetch.FetchDirectory].
   * Servers *MAY* expire content early, e.g. due to storage pressure.
   * </pre>
   *
   * <code>.google.protobuf.Timestamp expire_at = 4;</code>
   */
  com.google.protobuf.TimestampOrBuilder getExpireAtOrBuilder();

  /**
   * <pre>
   * Directory to associate
   * </pre>
   *
   * <code>.build.bazel.remote.execution.v2.Digest root_directory_digest = 5;</code>
   * @return Whether the rootDirectoryDigest field is set.
   */
  boolean hasRootDirectoryDigest();
  /**
   * <pre>
   * Directory to associate
   * </pre>
   *
   * <code>.build.bazel.remote.execution.v2.Digest root_directory_digest = 5;</code>
   * @return The rootDirectoryDigest.
   */
  build.bazel.remote.execution.v2.Digest getRootDirectoryDigest();
  /**
   * <pre>
   * Directory to associate
   * </pre>
   *
   * <code>.build.bazel.remote.execution.v2.Digest root_directory_digest = 5;</code>
   */
  build.bazel.remote.execution.v2.DigestOrBuilder getRootDirectoryDigestOrBuilder();

  /**
   * <pre>
   * Referenced blobs or directories that need to not expire before expiration
   * of this association, in addition to `root_directory_digest` itself.
   * These fields are hints - clients *MAY* omit them, and servers *SHOULD*
   * respect them, at the risk of increased incidents of Fetch responses
   * indirectly referencing unavailable blobs.
   * </pre>
   *
   * <code>repeated .build.bazel.remote.execution.v2.Digest references_blobs = 6;</code>
   */
  java.util.List<build.bazel.remote.execution.v2.Digest> 
      getReferencesBlobsList();
  /**
   * <pre>
   * Referenced blobs or directories that need to not expire before expiration
   * of this association, in addition to `root_directory_digest` itself.
   * These fields are hints - clients *MAY* omit them, and servers *SHOULD*
   * respect them, at the risk of increased incidents of Fetch responses
   * indirectly referencing unavailable blobs.
   * </pre>
   *
   * <code>repeated .build.bazel.remote.execution.v2.Digest references_blobs = 6;</code>
   */
  build.bazel.remote.execution.v2.Digest getReferencesBlobs(int index);
  /**
   * <pre>
   * Referenced blobs or directories that need to not expire before expiration
   * of this association, in addition to `root_directory_digest` itself.
   * These fields are hints - clients *MAY* omit them, and servers *SHOULD*
   * respect them, at the risk of increased incidents of Fetch responses
   * indirectly referencing unavailable blobs.
   * </pre>
   *
   * <code>repeated .build.bazel.remote.execution.v2.Digest references_blobs = 6;</code>
   */
  int getReferencesBlobsCount();
  /**
   * <pre>
   * Referenced blobs or directories that need to not expire before expiration
   * of this association, in addition to `root_directory_digest` itself.
   * These fields are hints - clients *MAY* omit them, and servers *SHOULD*
   * respect them, at the risk of increased incidents of Fetch responses
   * indirectly referencing unavailable blobs.
   * </pre>
   *
   * <code>repeated .build.bazel.remote.execution.v2.Digest references_blobs = 6;</code>
   */
  java.util.List<? extends build.bazel.remote.execution.v2.DigestOrBuilder> 
      getReferencesBlobsOrBuilderList();
  /**
   * <pre>
   * Referenced blobs or directories that need to not expire before expiration
   * of this association, in addition to `root_directory_digest` itself.
   * These fields are hints - clients *MAY* omit them, and servers *SHOULD*
   * respect them, at the risk of increased incidents of Fetch responses
   * indirectly referencing unavailable blobs.
   * </pre>
   *
   * <code>repeated .build.bazel.remote.execution.v2.Digest references_blobs = 6;</code>
   */
  build.bazel.remote.execution.v2.DigestOrBuilder getReferencesBlobsOrBuilder(
      int index);

  /**
   * <code>repeated .build.bazel.remote.execution.v2.Digest references_directories = 7;</code>
   */
  java.util.List<build.bazel.remote.execution.v2.Digest> 
      getReferencesDirectoriesList();
  /**
   * <code>repeated .build.bazel.remote.execution.v2.Digest references_directories = 7;</code>
   */
  build.bazel.remote.execution.v2.Digest getReferencesDirectories(int index);
  /**
   * <code>repeated .build.bazel.remote.execution.v2.Digest references_directories = 7;</code>
   */
  int getReferencesDirectoriesCount();
  /**
   * <code>repeated .build.bazel.remote.execution.v2.Digest references_directories = 7;</code>
   */
  java.util.List<? extends build.bazel.remote.execution.v2.DigestOrBuilder> 
      getReferencesDirectoriesOrBuilderList();
  /**
   * <code>repeated .build.bazel.remote.execution.v2.Digest references_directories = 7;</code>
   */
  build.bazel.remote.execution.v2.DigestOrBuilder getReferencesDirectoriesOrBuilder(
      int index);
}