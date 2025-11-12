package com.inkisle.mapper;

import com.inkisle.entity.UserPointAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserPointAccountMapper {

    UserPointAccount selectByUserId(@Param("userId") Long userId);

    /**
     * 进账：余额和累计值一起加
     * 账户不存在就建一行，省掉「先查再插」——那个写法在并发首刷时会撞主键
     */
    int earn(@Param("userId") Long userId, @Param("points") int points);

    /**
     * 出账：条件更新，余额不够就影响 0 行
     * 不用「先查余额再判断」：查完到更新之间别人也在花，会扣成负数
     */
    int deduct(@Param("userId") Long userId, @Param("points") int points);

}
