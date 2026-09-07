package com.ssafy.upcycle.trade.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MaterialListingTests {

    @Test
    void listingStartsFullyAvailable() {
        MaterialListing listing = listing(30_000L);

        assertThat(listing.getStatus()).isEqualTo(ListingStatus.ON_SALE);
        assertThat(listing.getTotalQuantity()).isEqualTo(30_000L);
        assertThat(listing.getAvailableQuantity()).isEqualTo(30_000L);
    }

    @Test
    void nonPositiveQuantityIsRejected() {
        assertThatThrownBy(() -> listing(0L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> listing(-1L)).isInstanceOf(IllegalArgumentException.class);
    }

    /** 부분 구매를 지원하므로 한 번 팔렸다고 바로 닫히지 않는다. */
    @Test
    void partialPurchaseLeavesTheListingOnSale() {
        MaterialListing listing = listing(30_000L);

        listing.reduceAvailable(10_000L);

        assertThat(listing.getAvailableQuantity()).isEqualTo(20_000L);
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.ON_SALE);
    }

    @Test
    void runningOutClosesTheListing() {
        MaterialListing listing = listing(30_000L);

        listing.reduceAvailable(10_000L);
        listing.reduceAvailable(20_000L);

        assertThat(listing.getAvailableQuantity()).isZero();
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.SOLD_OUT);
    }

    @Test
    void cannotSellMoreThanIsLeft() {
        MaterialListing listing = listing(30_000L);

        assertThatThrownBy(() -> listing.reduceAvailable(30_001L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(listing.getAvailableQuantity()).isEqualTo(30_000L);
    }

    @Test
    void soldOutListingRejectsFurtherSales() {
        MaterialListing listing = listing(10_000L);
        listing.reduceAvailable(10_000L);

        assertThatThrownBy(() -> listing.reduceAvailable(1_000L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void restoringQuantityReopensTheListing() {
        MaterialListing listing = listing(10_000L);
        listing.reduceAvailable(10_000L);

        listing.restoreAvailable(10_000L);

        assertThat(listing.getAvailableQuantity()).isEqualTo(10_000L);
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.ON_SALE);
    }

    @Test
    void cannotRestoreBeyondTheRegisteredQuantity() {
        MaterialListing listing = listing(10_000L);

        assertThatThrownBy(() -> listing.restoreAvailable(1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private MaterialListing listing(long totalQuantity) {
        return MaterialListing.open(1L, 10L, totalQuantity, 10_000L, 100L);
    }
}
