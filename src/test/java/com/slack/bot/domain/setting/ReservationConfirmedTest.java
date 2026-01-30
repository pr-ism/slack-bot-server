package com.slack.bot.domain.setting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.slack.bot.domain.setting.vo.ReservationConfirmed;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReservationConfirmedTest {

    @Test
    void 리뷰_예약_완료_확인_메시지_전달_공간은_기본적으로_DM이다() {
        // when & then
        ReservationConfirmed reservationConfirmed = assertDoesNotThrow(ReservationConfirmed::defaults);

        assertThat(reservationConfirmed.getDeliverySpace()).isEqualTo(DeliverySpace.DM);
    }

    @Test
    void 리뷰_예약_완료_확인_메시지_전달_공간을_트리거_채널로_변경한다() {
        // given
        ReservationConfirmed reservationConfirmed = ReservationConfirmed.defaults();

        // when
        ReservationConfirmed changed = reservationConfirmed.changeSpace(DeliverySpace.TRIGGER_CHANNEL);

        // then
        assertThat(changed.getDeliverySpace()).isEqualTo(DeliverySpace.TRIGGER_CHANNEL);
    }

    @Test
    void 리뷰_예약_완료_확인_메시지_전달_공간은_비어_있을_수_없다() {
        // given
        ReservationConfirmed reservationConfirmed = ReservationConfirmed.defaults();

        // when & then
        assertThatThrownBy(() -> reservationConfirmed.changeSpace(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("알림이 전달된 장소는 비어 있을 수 없습니다.");
    }
}

