import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import el from './locales/el.json'
import en from './locales/en.json'

export const languages = ['el', 'en'] as const
export type Language = (typeof languages)[number]

const stored = (() => {
  try {
    return localStorage.getItem('lang')
  } catch {
    return null
  }
})()

void i18n.use(initReactI18next).init({
  resources: { el: { translation: el }, en: { translation: en } },
  lng: stored === 'en' ? 'en' : 'el',
  fallbackLng: 'el',
  interpolation: { escapeValue: false },
})

i18n.on('languageChanged', (lng) => {
  document.documentElement.lang = lng
  document.title = i18n.t('app.title')
  try {
    localStorage.setItem('lang', lng)
  } catch {
    /* storage unavailable */
  }
})
document.documentElement.lang = i18n.language
document.title = i18n.t('app.title')

export default i18n
