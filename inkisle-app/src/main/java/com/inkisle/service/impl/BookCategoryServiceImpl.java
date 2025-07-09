package com.inkisle.service.impl;

import com.inkisle.common.BusinessException;
import com.inkisle.dto.CategorySaveDTO;
import com.inkisle.entity.BookCategory;
import com.inkisle.mapper.BookCategoryMapper;
import com.inkisle.mapper.BookInfoMapper;
import com.inkisle.service.BookCategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 分类树
 * 分类为两级，`parent_id` 为 0 的为顶层
 * 数据量仅数十条，故一次全部查出，在内存中组装 `children`
 */
@Service
public class BookCategoryServiceImpl implements BookCategoryService {

    /** 一级分类的 parent_id 约定为 0 */
    private static final long ROOT_ID = 0L;

    @Autowired
    private BookCategoryMapper bookCategoryMapper;

    @Autowired
    private BookInfoMapper bookInfoMapper;

    @Override
    public List<BookCategory> tree() {
        List<BookCategory> all = bookCategoryMapper.selectAll();

        List<BookCategory> roots = new ArrayList<>();
        for (BookCategory category : all) {
            if (category.getParentId() == ROOT_ID) {
                roots.add(category);
            }
        }

        // 分类仅数十条，内存中双层循环即可完成组装，无需为此数据量再查库
        for (BookCategory root : roots) {
            List<BookCategory> children = new ArrayList<>();
            for (BookCategory category : all) {
                if (category.getParentId().equals(root.getId())) {
                    children.add(category);
                }
            }
            root.setChildren(children);
        }
        return roots;
    }

    @Override
    public void create(CategorySaveDTO dto) {
        BookCategory category = new BookCategory();
        fill(category, dto, null);
        bookCategoryMapper.insert(category);
    }

    @Override
    public void update(Long id, CategorySaveDTO dto) {
        BookCategory exist = bookCategoryMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException("分类不存在");
        }
        // 编辑只开放名称与排序。改父级会让整棵树移位：把一个带子分类的一级分类
        // 挂到另一个一级分类下，它的子分类就成了三级，树组装时再也遍历不到，
        // 而作品仍在引用它们。故忽略请求体里的 parentId，一律沿用库中的值
        dto.setParentId(exist.getParentId());
        fill(exist, dto, id);
        bookCategoryMapper.update(exist);
    }

    @Override
    public void delete(Long id) {
        BookCategory exist = bookCategoryMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException("分类不存在");
        }
        if (bookCategoryMapper.countChildren(id) > 0) {
            throw new BusinessException("该分类下还有子分类，请先删除子分类");
        }
        // 用不过滤上架状态的计数：未过审、已下架的书同样引用了该分类，
        // 只看已上架作品会把它们漏掉，删完分类这些书就挂空了
        if (bookInfoMapper.countAllByCategory(id) > 0) {
            throw new BusinessException("该分类下还有作品，不能删除");
        }
        bookCategoryMapper.deleteById(id);
    }

    /**
     * 校验并填充。selfId 为空表示新增，非空表示修改时排除自身做重名检查
     * 分类只有两级：父分类本身必须是一级分类，这条同时挡掉了「把分类挂到自己子分类下」形成环的情况
     */
    private void fill(BookCategory category, CategorySaveDTO dto, Long selfId) {
        String name = dto.getCategoryName() == null ? "" : dto.getCategoryName().trim();
        if (name.isEmpty()) {
            throw new BusinessException("分类名不能为空");
        }

        long parentId = dto.getParentId() == null ? ROOT_ID : dto.getParentId();
        if (parentId != ROOT_ID) {
            if (selfId != null && parentId == selfId) {
                throw new BusinessException("不能把分类挂到自己下面");
            }
            BookCategory parent = bookCategoryMapper.selectById(parentId);
            if (parent == null) {
                throw new BusinessException("父分类不存在");
            }
            if (parent.getParentId() == null || parent.getParentId() != ROOT_ID) {
                throw new BusinessException("分类只支持两级，不能挂到二级分类下");
            }
        }

        if (bookCategoryMapper.countByName(name, parentId, selfId) > 0) {
            throw new BusinessException("同级下已有同名分类");
        }

        category.setCategoryName(name);
        category.setParentId(parentId);
        category.setSort(dto.getSort() == null ? 0 : dto.getSort());
    }

}
