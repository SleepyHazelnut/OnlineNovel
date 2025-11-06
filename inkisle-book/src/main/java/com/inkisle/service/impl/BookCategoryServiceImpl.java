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
 * 分类树。就两级，parent_id 是 0 的算顶层
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
            // parent_id 允许为空，Long 与 long 直接 == 会拆箱，遇到 null 就是全站 500
            if (Long.valueOf(ROOT_ID).equals(category.getParentId())) {
                roots.add(category);
            }
        }

        // 就几十条，全查出来在内存里拼，不用为这点数据再查一次库
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
        // 编辑只让改名字和排序。父级一动整棵树就错位了，比如把一个带子分类的一级分类
        // 挂到另一个一级分类下面，它的子分类就成了三级，组装树的时候根本遍历不到，
        // 但书还指着这些分类。所以请求体里的 parentId 直接不认
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
        // 这里不过滤上架状态，没审过、下架的书也引用了这个分类，
        // 只看已上架会漏掉，删完那些书就挂空了
        if (bookInfoMapper.countAllByCategory(id) > 0) {
            throw new BusinessException("该分类下还有作品，不能删除");
        }
        bookCategoryMapper.deleteById(id);
    }

    /**
     * 校验加填充。selfId 空的是新增，非空是修改，重名检查时要排除自己
     * 父分类必须是一级分类，该判断同时阻止把分类挂到自身子分类下形成环
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
