package com.avemygo.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.avemygo.admin.AdminEventService;
import com.avemygo.admin.AdminService;
import com.avemygo.admin.dto.AdminDtos.SongPatch;
import com.avemygo.admin.dto.AdminDtos.SongUpsert;
import com.avemygo.admin.dto.AdminEventDtos.EventPatch;
import com.avemygo.admin.dto.AdminEventDtos.EventUpsert;
import com.avemygo.admin.dto.AdminEventDtos.SessionUpsert;
import com.avemygo.event.EventService;
import com.avemygo.favorite.FavoriteService;
import com.avemygo.playlist.PlaylistService;
import com.avemygo.reminder.ReminderService;
import com.avemygo.song.SongService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** 状态改变后，用户集合隐藏内容但保留关联，重新发布即可恢复。 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ContentVisibilityTest {

    private static final Long USER = 98765491L;
    @Autowired private AdminService adminSongs;
    @Autowired private AdminEventService adminEvents;
    @Autowired private SongService songs;
    @Autowired private EventService events;
    @Autowired private PlaylistService playlists;
    @Autowired private FavoriteService favorites;
    @Autowired private ReminderService reminders;
    @Autowired private JdbcTemplate jdbc;

    private Long hiddenSong;
    private Long visibleSong;

    @BeforeEach
    void setUp() {
        hiddenSong = createSong("[TEST] visibility-hidden", "/img/hidden.jpg");
        visibleSong = createSong("[TEST] visibility-visible", "/img/visible.jpg");
    }

    @Test
    void playlistHidesUnlistedSongsInDetailCountAndCoverThenRestoresThem() {
        Long playlist = playlists.create(USER, "可见性测试", null).id();
        playlists.addSong(USER, playlist, hiddenSong);
        playlists.addSong(USER, playlist, visibleSong);
        status((byte) 0);

        assertThat(playlists.detail(USER, playlist).songs()).extracting(song -> song.id()).containsExactly(visibleSong);
        assertThat(playlists.list(USER)).singleElement().satisfies(summary -> {
            assertThat(summary.songCount()).isEqualTo(1);
            assertThat(summary.coverUrl()).isEqualTo("/img/visible.jpg");
        });
        status((byte) 1);
        assertThat(playlists.detail(USER, playlist).songs()).extracting(song -> song.id()).containsExactly(hiddenSong, visibleSong);
    }

    @Test
    void adminCanFindUnlistedSongsWhilePublicSearchCannot() {
        status((byte) 0);
        assertThat(adminSongs.songs((short) 1, "visibility-hidden", 1, 20).getContent())
                .singleElement().satisfies(song -> assertThat(song.status()).isZero());
        assertThat(songs.list((short) 1, "visibility-hidden", null, null, 1, 20)).isEmpty();
        assertThatThrownBy(() -> songs.detail(hiddenSong)).isInstanceOf(BizException.class);
    }

    @Test
    void favoritesHideUnlistedSongsAndRejectNewFavoritesUntilRepublished() {
        favorites.add(USER, "SONG", hiddenSong);
        favorites.add(USER, "SONG", visibleSong);
        status((byte) 0);
        assertThat(favorites.list(USER, "SONG")).extracting(item -> item.targetId()).containsExactly(visibleSong);
        assertThatThrownBy(() -> favorites.add(USER, "SONG", hiddenSong))
                .isInstanceOfSatisfying(BizException.class, error -> assertThat(error.getCode()).isEqualTo(40401));
        status((byte) 1);
        assertThat(favorites.list(USER, "SONG")).extracting(item -> item.targetId()).contains(hiddenSong, visibleSong);
    }

    @ParameterizedTest
    @ValueSource(strings = {"favorites", "reminders"})
    void eventCollectionsHideDraftsAndRestoreExistingSubscriptions(String collection) {
        Long event = createEvent();
        favorites.add(USER, "EVENT", event);
        reminders.subscribe(USER, event);
        eventStatus(event, "DRAFT");
        if ("favorites".equals(collection)) {
            assertThat(favorites.list(USER, "EVENT")).isEmpty();
        } else {
            assertThat(reminders.myReminders(USER)).isEmpty();
        }
        eventStatus(event, "PUBLISHED");
        assertThat(favorites.list(USER, "EVENT")).singleElement();
        assertThat(reminders.myReminders(USER)).singleElement();
    }

    @Test
    void publicSetlistHidesUnlistedSongMetadata() {
        Long event = createEvent();
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        Long session = adminEvents.createSession(event, new SessionUpsert("[TEST] 可见曲表", start,
                start.plusHours(2), start.minusDays(1), "ONSALE")).id();
        jdbc.update("INSERT INTO setlist_item(session_id, song_id, order_no, is_encore) VALUES (?, ?, 1, 0), (?, ?, 2, 0)",
                session, hiddenSong, session, visibleSong);
        status((byte) 0);
        assertThat(events.setlist(session)).extracting(item -> item.songId()).containsExactly(visibleSong);
    }

    private Long createSong(String title, String cover) {
        return adminSongs.create(new SongUpsert(title, (short) 1, null, null, null,
                180, cover, null, null, null, "", (byte) 1)).id();
    }

    private void status(byte status) {
        adminSongs.update(hiddenSong, new SongPatch(null, null, null, null, null, null,
                null, null, null, null, status));
    }

    private Long createEvent() {
        return adminEvents.create(new EventUpsert("[TEST] visibility-event", (short) 1, null,
                null, "测试场馆", "PUBLISHED")).id();
    }

    private void eventStatus(Long event, String status) {
        adminEvents.update(event, new EventPatch(null, null, null, null, null, status));
    }
}
