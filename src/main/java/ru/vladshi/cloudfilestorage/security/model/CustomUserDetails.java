package ru.vladshi.cloudfilestorage.security.model;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import ru.vladshi.cloudfilestorage.user.entity.User;

import java.util.Collection;

@Getter
public class CustomUserDetails extends org.springframework.security.core.userdetails.User {

    private final Long id;

    public CustomUserDetails(User user, Collection<? extends GrantedAuthority> authorities) {
        super(user.getUsername(), user.getPassword(), authorities);
        this.id = user.getId();
    }

}
