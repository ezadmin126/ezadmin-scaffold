package top.ezadmin.scaffold.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import top.ezadmin.scaffold.domain.Asset;

import java.util.List;
import java.util.Optional;

@Mapper
public interface AssetMapper {
    String ACTIVE_ASSETS = "SELECT ID, ASSET_NAME AS NAME, NOTES FROM DEMO_ASSET WHERE DELETED = FALSE";

    @Select(ACTIVE_ASSETS + " ORDER BY ID DESC")
    List<Asset> findAll();

    @Select(ACTIVE_ASSETS + " AND ID = #{id}")
    Optional<Asset> findById(@Param("id") long id);

    @Insert("INSERT INTO DEMO_ASSET (ASSET_NAME, NOTES) VALUES (#{name}, #{notes})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "ID")
    void insert(Asset asset);

    @Update("UPDATE DEMO_ASSET SET ASSET_NAME = #{name}, NOTES = #{notes} WHERE ID = #{id} AND DELETED = FALSE")
    int update(Asset asset);

    @Update("UPDATE DEMO_ASSET SET DELETED = TRUE WHERE ID = #{id} AND DELETED = FALSE")
    int delete(@Param("id") long id);
}
