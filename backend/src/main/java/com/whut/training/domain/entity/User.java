package com.whut.training.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.whut.training.domain.enums.MemberType;
import com.whut.training.domain.enums.UserRole;

/**
 * 用户实体。
 *
 * <p>承载登录、个人资料和 Codeforces 统计信息。password 仅保存不可逆哈希且不会序列化到接口响应。
 */
public class User {
    private Long id;
    private String username;
    private String email;
    @JsonIgnore
    private String password;
    private UserRole role;
    private Long uid;
    private Integer codeforcesRating;
    private Integer maxRating;
    private Boolean online;
    private Long lastOnlineTimeSeconds;
    private String avatarUrl;
    private Boolean avatarCustomized;
    private Integer totalPoints;
    private String displayName;
    private String realName;
    private String college;
    private String className;
    private String bio;
    private String codeforcesHandle;
    private String pendingCodeforcesHandle;
    private Long codeforcesBindingStartedAtSeconds;
    private String atcoderHandle;
    private String pendingAtcoderHandle;
    @JsonIgnore
    private String atcoderBindingToken;
    private Long atcoderBindingStartedAtSeconds;
    private Long atcoderVerifiedAtSeconds;
    private MemberType memberType = MemberType.REGULAR;
    private Boolean showProblemTags = true;

    /**
     * 无参构造器，供反射或框架映射使用。
     */
    public User() {
    }

    /**
     * 创建基础用户实体。
     *
     * @param id       用户 ID。
     * @param username 用户名。
     * @param email    邮箱。
     * @param password 密码。
     * @param role     用户角色。
     */
    public User(Long id, String username, String email, String password, UserRole role) {
        this(id, username, email, password, role, null, null, null, null, null);
    }

    /**
     * 创建带 Codeforces 统计信息的用户实体。
     *
     * @param id                 用户 ID。
     * @param username           用户名。
     * @param email              邮箱。
     * @param password           密码。
     * @param role               用户角色。
     * @param codeforcesRating   当前 rating。
     * @param maxRating          历史最高 rating。
     */
    public User(Long id, String username, String email, String password, UserRole role, Integer codeforcesRating, Integer maxRating) {
        this(id, username, email, password, role, codeforcesRating, maxRating, null, null, null);
    }

    /**
     * 创建带在线状态和头像信息的用户实体。
     *
     * @param id                 用户 ID。
     * @param username           用户名。
     * @param email              邮箱。
     * @param password           密码。
     * @param role               用户角色。
     * @param codeforcesRating   当前 rating。
     * @param maxRating          历史最高 rating。
     * @param online             在线状态。
     * @param lastOnlineTimeSeconds 最近在线时间戳。
     * @param avatarUrl          头像地址。
     */
    public User(Long id, String username, String email, String password, UserRole role, Integer codeforcesRating, Integer maxRating,
                Boolean online, Long lastOnlineTimeSeconds, String avatarUrl) {
        this(id, username, email, password, role, codeforcesRating, maxRating, online, lastOnlineTimeSeconds, avatarUrl, null);
    }

    /**
     * 创建完整用户实体。
     *
     * @param id                 用户 ID。
     * @param username           用户名。
     * @param email              邮箱。
     * @param password           密码。
     * @param role               用户角色。
     * @param codeforcesRating   当前 rating。
     * @param maxRating          历史最高 rating。
     * @param online             在线状态。
     * @param lastOnlineTimeSeconds 最近在线时间戳。
     * @param avatarUrl          头像地址。
     * @param uid                Codeforces UID。
     */
    public User(Long id, String username, String email, String password, UserRole role, Integer codeforcesRating, Integer maxRating,
                Boolean online, Long lastOnlineTimeSeconds, String avatarUrl, Long uid) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.password = password;
        this.role = role;
        this.uid = uid;
        this.codeforcesRating = codeforcesRating;
        this.maxRating = maxRating;
        this.online = online;
        this.lastOnlineTimeSeconds = lastOnlineTimeSeconds;
        this.avatarUrl = avatarUrl;
    }

    /**
     * 获取用户 ID。
     *
     * @return 用户 ID。
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置用户 ID。
     *
     * @param id 用户 ID。
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 获取用户名。
     *
     * @return 用户名。
     */
    public String getUsername() {
        return username;
    }

    /**
     * 设置用户名。
     *
     * @param username 用户名。
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * 获取邮箱。
     *
     * @return 邮箱。
     */
    public String getEmail() {
        return email;
    }

    /**
     * 设置邮箱。
     *
     * @param email 邮箱。
     */
    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * 获取密码。
     *
     * @return 密码。
     */
    public String getPassword() {
        return password;
    }

    /**
     * 设置密码。
     *
     * @param password 密码。
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * 获取用户角色。
     *
     * @return 用户角色。
     */
    public UserRole getRole() {
        return role;
    }

    /**
     * 设置用户角色。
     *
     * @param role 用户角色。
     */
    public void setRole(UserRole role) {
        this.role = role;
    }

    /**
     * 获取 Codeforces UID。
     *
     * @return UID。
     */
    public Long getUid() {
        return uid;
    }

    /**
     * 设置 Codeforces UID。
     *
     * @param uid UID。
     */
    public void setUid(Long uid) {
        this.uid = uid;
    }

    /**
     * 获取当前 rating。
     *
     * @return 当前 rating。
     */
    public Integer getCodeforcesRating() {
        return codeforcesRating;
    }

    /**
     * 设置当前 rating。
     *
     * @param codeforcesRating 当前 rating。
     */
    public void setCodeforcesRating(Integer codeforcesRating) {
        this.codeforcesRating = codeforcesRating;
    }

    /**
     * 获取历史最高 rating。
     *
     * @return 历史最高 rating。
     */
    public Integer getMaxRating() {
        return maxRating;
    }

    /**
     * 设置历史最高 rating。
     *
     * @param maxRating 历史最高 rating。
     */
    public void setMaxRating(Integer maxRating) {
        this.maxRating = maxRating;
    }

    /**
     * 获取在线状态。
     *
     * @return 在线状态。
     */
    public Boolean getOnline() {
        return online;
    }

    /**
     * 设置在线状态。
     *
     * @param online 在线状态。
     */
    public void setOnline(Boolean online) {
        this.online = online;
    }

    /**
     * 获取最近在线时间戳。
     *
     * @return 最近在线时间戳。
     */
    public Long getLastOnlineTimeSeconds() {
        return lastOnlineTimeSeconds;
    }

    /**
     * 设置最近在线时间戳。
     *
     * @param lastOnlineTimeSeconds 最近在线时间戳。
     */
    public void setLastOnlineTimeSeconds(Long lastOnlineTimeSeconds) {
        this.lastOnlineTimeSeconds = lastOnlineTimeSeconds;
    }

    /**
     * 获取头像地址。
     *
     * @return 头像地址。
     */
    public String getAvatarUrl() {
        return avatarUrl;
    }

    /**
     * 设置头像地址。
     *
     * @param avatarUrl 头像地址。
     */
    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public Boolean getAvatarCustomized() {
        return avatarCustomized;
    }

    public void setAvatarCustomized(Boolean avatarCustomized) {
        this.avatarCustomized = avatarCustomized;
    }

    /**
     * 获取用户总积分（每日一题累计）。
     *
     * @return 总积分（可能为 null 表示未初始化）。
     */
    public Integer getTotalPoints() {
        return totalPoints;
    }

    /**
     * 设置用户总积分。
     *
     * @param totalPoints 总积分。
     */
    public void setTotalPoints(Integer totalPoints) {
        this.totalPoints = totalPoints;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public String getCollege() {
        return college;
    }

    public void setCollege(String college) {
        this.college = college;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getCodeforcesHandle() {
        return codeforcesHandle;
    }

    public void setCodeforcesHandle(String codeforcesHandle) {
        this.codeforcesHandle = codeforcesHandle;
    }

    public String getPendingCodeforcesHandle() {
        return pendingCodeforcesHandle;
    }

    public void setPendingCodeforcesHandle(String pendingCodeforcesHandle) {
        this.pendingCodeforcesHandle = pendingCodeforcesHandle;
    }

    public Long getCodeforcesBindingStartedAtSeconds() {
        return codeforcesBindingStartedAtSeconds;
    }

    public void setCodeforcesBindingStartedAtSeconds(Long codeforcesBindingStartedAtSeconds) {
        this.codeforcesBindingStartedAtSeconds = codeforcesBindingStartedAtSeconds;
    }

    public boolean isCodeforcesBound() {
        return codeforcesHandle != null && !codeforcesHandle.isBlank();
    }

    public String getAtcoderHandle() {
        return atcoderHandle;
    }

    public void setAtcoderHandle(String atcoderHandle) {
        this.atcoderHandle = atcoderHandle;
    }

    public String getPendingAtcoderHandle() {
        return pendingAtcoderHandle;
    }

    public void setPendingAtcoderHandle(String pendingAtcoderHandle) {
        this.pendingAtcoderHandle = pendingAtcoderHandle;
    }

    public String getAtcoderBindingToken() {
        return atcoderBindingToken;
    }

    public void setAtcoderBindingToken(String atcoderBindingToken) {
        this.atcoderBindingToken = atcoderBindingToken;
    }

    public Long getAtcoderBindingStartedAtSeconds() {
        return atcoderBindingStartedAtSeconds;
    }

    public void setAtcoderBindingStartedAtSeconds(Long atcoderBindingStartedAtSeconds) {
        this.atcoderBindingStartedAtSeconds = atcoderBindingStartedAtSeconds;
    }

    public Long getAtcoderVerifiedAtSeconds() {
        return atcoderVerifiedAtSeconds;
    }

    public void setAtcoderVerifiedAtSeconds(Long atcoderVerifiedAtSeconds) {
        this.atcoderVerifiedAtSeconds = atcoderVerifiedAtSeconds;
    }

    public boolean isAtcoderBound() {
        return atcoderHandle != null && !atcoderHandle.isBlank() && atcoderVerifiedAtSeconds != null;
    }

    public MemberType getMemberType() {
        return memberType;
    }

    public void setMemberType(MemberType memberType) {
        this.memberType = memberType;
    }

    public Boolean getShowProblemTags() {
        return showProblemTags;
    }

    public void setShowProblemTags(Boolean showProblemTags) {
        this.showProblemTags = showProblemTags;
    }
}
