package top.ezadmin.scaffold.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.ezadmin.scaffold.domain.Asset;
import top.ezadmin.scaffold.mapper.AssetMapper;

import java.util.List;
import java.util.Optional;

@Service
public class AssetService {
    private final AssetMapper mapper;

    public AssetService(AssetMapper mapper) {
        this.mapper = mapper;
    }

    public List<Asset> findAll() {
        return mapper.findAll();
    }

    public Optional<Asset> findById(long id) {
        return mapper.findById(id);
    }

    @Transactional
    public Asset create(String name, String notes) {
        Asset asset = validatedAsset(null, name, notes);
        mapper.insert(asset);
        return asset;
    }

    @Transactional
    public Optional<Asset> update(long id, String name, String notes) {
        Asset asset = validatedAsset(id, name, notes);
        return mapper.update(asset) == 0 ? Optional.empty() : Optional.of(asset);
    }

    @Transactional
    public boolean delete(long id) {
        return mapper.delete(id) == 1;
    }

    private Asset validatedAsset(Long id, String name, String notes) {
        String trimmed = name == null ? "" : name.strip();
        if (trimmed.isEmpty() || trimmed.length() > 100) {
            throw new IllegalArgumentException("资产名称为必填项，最多 100 个字符。");
        }
        if (notes != null && notes.length() > 300) {
            throw new IllegalArgumentException("备注最多 300 个字符。");
        }
        return new Asset(id, trimmed, notes);
    }
}
