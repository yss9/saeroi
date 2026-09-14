package com.ssafy.upcycle.user.dto;

import com.ssafy.upcycle.user.entity.ConsumerAddress;

/** 배송지 한 건. {@code /api/v1/consumers/me/addresses} 계열 응답의 데이터다. */
public record ConsumerAddressResponse(
        Long addressId,
        String alias,
        String receiverName,
        String receiverPhone,
        String zipCode,
        String address,
        String addressDetail,
        boolean isDefault) {

    public static ConsumerAddressResponse from(ConsumerAddress address) {
        return new ConsumerAddressResponse(
                address.getId(),
                address.getAlias(),
                address.getReceiverName(),
                address.getReceiverPhone(),
                address.getZipCode(),
                address.getAddress(),
                address.getAddressDetail(),
                address.isDefault());
    }
}
