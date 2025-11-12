package com.inkisle.mapper;

import com.inkisle.dto.BookVotesDTO;
import com.inkisle.entity.UserRecommendVote;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserRecommendVoteMapper {

    /** 投票，一人一本一票，重复投返回 0 行 */
    int insertIgnore(UserRecommendVote vote);

    int countByBook(@Param("bookId") Long bookId);

    /** 票数榜，票数相同按最早投出的在前，避免同票数的书来回换位 */
    List<BookVotesDTO> selectTop(@Param("limit") int limit);

}
