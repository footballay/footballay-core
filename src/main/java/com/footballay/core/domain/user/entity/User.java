package com.footballay.core.domain.user.entity;

import com.footballay.core.domain.football.preference.persistence.PreferenceKey;
import com.footballay.core.entity.BaseDateAuditEntity;
import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

/**
 * 회원 정보 엔티티
 *
 * "테이블 이름 단수형 사용 원칙"에 예외 케이스로 "Users"는 테이블 이름을 복수형을 사용합니다.
 * H2 DB 예약어 "USER"와 충돌 방지를 위해 테이블 명을 "users"로 지정합니다.
 */
@Entity
@Table(name = "users")
public class User extends BaseDateAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 50)
    private String username;
    @Column(nullable = true, unique = true, length = 30)
    private String nickname;
    /**
     * 프로필 이미지 URL
     */
    @Column(nullable = true)
    private String profileImage;
    @Column(nullable = false, length = 500)
    private String password;
    @Column(nullable = false)
    private boolean enabled;
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private Set<Authority> authorities;
    // 선호 설정의 cascade/orphanRemoval 책임이 있어 이번 역방향 제거 범위에서 제외합니다.
    // LAZY여도 연관 존재 확인 SELECT가 발생할 수 있습니다.
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private PreferenceKey preferenceKey;

    private static Set<Authority> $default$authorities() {
        return new HashSet<>();
    }


    public static class UserBuilder {
        private Long id;
        private String username;
        private String nickname;
        private String profileImage;
        private String password;
        private boolean enabled;
        private boolean authorities$set;
        private Set<Authority> authorities$value;
        private PreferenceKey preferenceKey;

        UserBuilder() {
        }

        /**
         * @return {@code this}.
         */
        public User.UserBuilder id(final Long id) {
            this.id = id;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public User.UserBuilder username(final String username) {
            this.username = username;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public User.UserBuilder nickname(final String nickname) {
            this.nickname = nickname;
            return this;
        }

        /**
         * 프로필 이미지 URL
         * @return {@code this}.
         */
        public User.UserBuilder profileImage(final String profileImage) {
            this.profileImage = profileImage;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public User.UserBuilder password(final String password) {
            this.password = password;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public User.UserBuilder enabled(final boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public User.UserBuilder authorities(final Set<Authority> authorities) {
            this.authorities$value = authorities;
            authorities$set = true;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public User.UserBuilder preferenceKey(final PreferenceKey preferenceKey) {
            this.preferenceKey = preferenceKey;
            return this;
        }

        public User build() {
            Set<Authority> authorities$value = this.authorities$value;
            if (!this.authorities$set) authorities$value = User.$default$authorities();
            return new User(this.id, this.username, this.nickname, this.profileImage, this.password, this.enabled, authorities$value, this.preferenceKey);
        }

        @java.lang.Override
        public java.lang.String toString() {
            return "User.UserBuilder(id=" + this.id + ", username=" + this.username + ", nickname=" + this.nickname + ", profileImage=" + this.profileImage + ", password=" + this.password + ", enabled=" + this.enabled + ", authorities$value=" + this.authorities$value + ", preferenceKey=" + this.preferenceKey + ")";
        }
    }

    public static User.UserBuilder builder() {
        return new User.UserBuilder();
    }

    public Long getId() {
        return this.id;
    }

    public String getUsername() {
        return this.username;
    }

    public String getNickname() {
        return this.nickname;
    }

    /**
     * 프로필 이미지 URL
     */
    public String getProfileImage() {
        return this.profileImage;
    }

    public String getPassword() {
        return this.password;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public Set<Authority> getAuthorities() {
        return this.authorities;
    }

    public PreferenceKey getPreferenceKey() {
        return this.preferenceKey;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public void setUsername(final String username) {
        this.username = username;
    }

    public void setNickname(final String nickname) {
        this.nickname = nickname;
    }

    /**
     * 프로필 이미지 URL
     */
    public void setProfileImage(final String profileImage) {
        this.profileImage = profileImage;
    }

    public void setPassword(final String password) {
        this.password = password;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    public void setAuthorities(final Set<Authority> authorities) {
        this.authorities = authorities;
    }

    public void setPreferenceKey(final PreferenceKey preferenceKey) {
        this.preferenceKey = preferenceKey;
    }

    /**
     * Creates a new {@code User} instance.
     *
     * @param id
     * @param username
     * @param nickname
     * @param profileImage 프로필 이미지 URL
     * @param password
     * @param enabled
     * @param authorities
     * @param preferenceKey
     */
    public User(final Long id, final String username, final String nickname, final String profileImage, final String password, final boolean enabled, final Set<Authority> authorities, final PreferenceKey preferenceKey) {
        this.id = id;
        this.username = username;
        this.nickname = nickname;
        this.profileImage = profileImage;
        this.password = password;
        this.enabled = enabled;
        this.authorities = authorities;
        this.preferenceKey = preferenceKey;
    }

    public User() {
        this.authorities = User.$default$authorities();
    }
}
