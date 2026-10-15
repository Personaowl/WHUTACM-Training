package com.whut.training.domain.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 用户注册请求。
 *
 * <p>username 是站内登录名。Codeforces 账号在注册并登录后通过独立的所有权验证流程绑定。
 *
 * @param username 登录账号。
 * @param displayName 对外展示用户名。
 * @param email    邮箱。
 * @param password 密码。
 */
public class UserRegisterRequest {

    @NotBlank(message = "username cannot be blank")
    @Size(max = 50, message = "username length must be <= 50")
    private String username;

    @NotBlank(message = "display name cannot be blank")
    @Size(max = 50, message = "display name length must be <= 50")
    private String displayName;

    @Size(max = 100, message = "real name length must be <= 100")
    private String realName;

    @Size(max = 150, message = "college length must be <= 150")
    private String college;

    @Size(max = 150, message = "class name length must be <= 150")
    private String className;

    @Email(message = "email format is invalid")
    @Size(max = 255, message = "email length must be <= 255")
    private String email;

    @NotBlank(message = "password cannot be blank")
    @Size(min = 8, max = 64, message = "password length must be 8-64")
    private String password;

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

    public String getDisplayName() {
        return displayName;
    }

    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }
    public String getCollege() { return college; }
    public void setCollege(String college) { this.college = college; }
    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
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

}
