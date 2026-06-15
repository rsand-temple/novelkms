import { Node, mergeAttributes } from '@tiptap/core'

export const DraftHeading = Node.create({
  name: 'draftHeading',
  group: 'block',
  atom: true,
  selectable: false,
  draggable: false,

  addAttributes() {
    return {
      kind: {
        default: 'chapter',
        parseHTML: element => element.getAttribute('data-draft-heading') || 'chapter',
      },
      entityId: {
        default: null,
        parseHTML: element => element.getAttribute('data-entity-id') || null,
      },
      title: {
        default: '',
        parseHTML: element => element.getAttribute('data-title') || '',
      },
      subtitle: {
        default: '',
        parseHTML: element => element.getAttribute('data-subtitle') || '',
      },
    }
  },

  parseHTML() {
    return [{ tag: 'div[data-draft-heading]' }]
  },

  renderHTML({ node, HTMLAttributes }) {
    const { kind, entityId, title, subtitle } = node.attrs
    return [
      'div',
      mergeAttributes(HTMLAttributes, {
        'data-draft-heading': kind,
        'data-entity-id': entityId || '',
        'data-title': title || '',
        'data-subtitle': subtitle || '',
        contenteditable: 'false',
        class: `nkms-draft-heading nkms-draft-heading-${kind}`,
      }),
    ]
  },
})
