interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'icon'
}

export function Button({ variant = 'primary', type = 'button', className, ...props }: ButtonProps) {
  const variantClass = `btn btn-${variant}`
  return (
    <button
      type={type}
      className={className ? `${variantClass} ${className}` : variantClass}
      {...props}
    />
  )
}
