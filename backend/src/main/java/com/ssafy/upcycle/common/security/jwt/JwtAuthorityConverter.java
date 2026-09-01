package com.ssafy.upcycle.common.security.jwt;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public class JwtAuthorityConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        Collection<SimpleGrantedAuthority> authorities = roles == null
                ? List.of()
                : roles.stream()
                        .filter(Objects::nonNull)
                        .filter(role -> !role.isBlank())
                        .collect(Collectors.toCollection(TreeSet::new))
                        .stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toList();
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }
}
